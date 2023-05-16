
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class Main
{
    public static void main(String[] args) throws IOException, ParseException
    {
        if (args.length < 4)
        {
            System.err.printf("Wrong parameters: %s\nExpecting BM25 IndexPath QueriesPath Output Docpath(optional to build index)\n", Arrays.toString(args));
            System.exit(1);
        }
        String method = args[0];
        String indexPath = args[1];
        String queryPath = args[2];
        String outputPath = args[3];
        String docpath = "Data";
        if (args.length == 5)
        {
            docpath = args[4];
        }
        index(indexPath, docpath);
        search(method, queryPath, indexPath, outputPath);
    }

    private static void index(String indexPath, String docPath)
    {
        File indexFile = new File(indexPath);
        boolean create = false;
        if (!indexFile.exists())
        {
            indexFile.mkdirs();
            create = true;
        }
        else if (indexFile.listFiles().length == 0)
        {
            create = true;
        }
        if (create)
        {
            System.err.printf("%s not exists, assume documents are in %s and we are indexing it...\n", indexPath, docPath);
            createOrUpdateIndex(docPath, indexPath, create);
        }
    }

    static void search(String method, String queries, String index, String outputPath) throws IOException, ParseException
    {
        method = method.toLowerCase();
        Map<String, Similarity> similarityMap = new HashMap<>();
        similarityMap.put("BM25".toLowerCase(), new BM25Similarity());
        similarityMap.put("LMLaplace".toLowerCase(), new LMDirichletSimilarity());
        similarityMap.put("RM1".toLowerCase(), new LMDirichletSimilarity());
        similarityMap.put("RM3".toLowerCase(), new LMDirichletSimilarity());
        similarityMap.put("LMDirichlet".toLowerCase(), new LMDirichletSimilarity());
        similarityMap.put("LMJelinekMercer".toLowerCase(), new LMJelinekMercerSimilarity(0.5f));
        Similarity similarity = similarityMap.get(method.toLowerCase());

        IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(index)));
        IndexSearcher searcher = new IndexSearcher(reader);
        if (similarity != null)
        {
            System.err.println("Use similarity " + similarity.toString());
            searcher.setSimilarity(similarity);
        }
        Analyzer analyzer = new AnalyzerStemmedStopword();
        String field = "contents";
        QueryParser parser = new QueryParser(field, analyzer);
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputPath), StandardCharsets.UTF_8));

        List<Topic> topics = Utils.readTopic(queries);
        int tid = 0;
        for (Topic topic : topics)
        {
            ++tid;
            String line = topic.getQuery();
            Query query = parser.parse(line);
            System.err.printf("%d / %d Searching for: %s\n", tid, topics.size(), query.toString(field));
            TopDocs topDocs = searcher.search(query, 1000);
            if (method.startsWith("rm"))
            {
                String[] analyzedQuery = query.toString(field).split("\\s");
                RelevanceBasedLanguageModel rlm = new RelevanceBasedLanguageModel(reader, topDocs, analyzedQuery);
                if ("rm1".equals(method))
                {
                    rlm.reRank(rlm.RM1(), topDocs, reader, analyzedQuery);
                }
                else
                {
                    rlm.reRank(rlm.RM3(analyzedQuery), topDocs, reader, analyzedQuery);
                }
            }

            for (int i = 0; i < topDocs.scoreDocs.length; i++)
            {
                ScoreDoc scoreDoc = topDocs.scoreDocs[i];
                Document doc = searcher.doc(scoreDoc.doc);
                String contents = doc.getField("contents").stringValue();
                String docno = doc.getField("docno").stringValue();
                int rank = i + 1;
                bw.write(String.format("%s\tQ0\t%s\t%d\t%.1f\tnetienne\n", topic.number, docno, rank, scoreDoc.score));
            }
        }
        bw.close();
        reader.close();
    }

    static void createOrUpdateIndex(String docsPath, String indexPath, boolean create)
    {
        final Path docDir = Paths.get(docsPath);
        if (!Files.isReadable(docDir))
        {
            System.out.println("Document directory '" + docDir.toAbsolutePath() + "' does not exist or is not readable, please check the path");
            System.exit(1);
        }

        Date start = new Date();
        try
        {
            System.out.println("Indexing to directory '" + indexPath + "'...");

            Directory dir = FSDirectory.open(Paths.get(indexPath));
            Analyzer analyzer = new AnalyzerStemmedStopword();
            IndexWriterConfig iwc = new IndexWriterConfig(analyzer);

            if (create)
            {
                // Create a new index in the directory, removing any
                // previously indexed documents:
                iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
            }
            else
            {
                // Add new documents to the existing index:
                iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            }

            // increase the RAM buffer to improve indexing .  If this is done then increase the max heap
            // size to the JVM (eg add -Xmx512m or -Xmx1g):
            // iwc.setRAMBufferSizeMB(256.0);

            IndexWriter writer = new IndexWriter(dir, iwc);
            indexDocs(writer, docDir);

            //  forceMerge to increase search performance.  
            // writer.forceMerge(1);

            writer.close();

            Date end = new Date();
            System.out.println(end.getTime() - start.getTime() + " total milliseconds");

        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * The file indexes using the  writer, or if a specified directory,
     * recurses over files and directories found under the given directory.
     * <p>
     * The method indexes one document per file. Muultiple documents can be placed into the file(s). Sample
     * using the
     * <a href="../../../../../contrib-benchmark/org/apache/lucene/benchmark/byTask/tasks/WriteLineDocTask.html"
     * >WriteLineDocTask</a>.
     *
     * @param writer Writer to the index where the file or directory info will be stored
     * @param path   The file to index, or the directory that can recurse into to find files to index
     * @throws IOException If there is a low-level I/O error
     */
    static void indexDocs(final IndexWriter writer, Path path) throws IOException
    {
        if (Files.isDirectory(path))
        {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>()
            {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
                {
                    try
                    {
                        indexDoc(writer, file, attrs.lastModifiedTime().toMillis());
                    }
                    catch (IOException ignore)
                    {
                        // ensures that you index files that are not readable. 
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        else
        {

            indexDoc(writer, path, Files.getLastModifiedTime(path).toMillis());
        }
    }

    /**
     * Indexes a single document
     */
    static void indexDoc(IndexWriter writer, Path file, long lastModified) throws IOException
    {
        String dirname = file.getParent().getFileName().toString();
        if (!dirname.endsWith("latimes") && !dirname.endsWith("fbis") && !dirname.endsWith("ft"))
        {
            return;
        }
        String filename = file.getFileName().toString();
        if (filename.contains(".") || filename.contains("read"))
        {
            return;
        }
        try (InputStream stream = Files.newInputStream(file))
        {
            // make a new, empty document

            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            List<Doc> parsedDoc = Utils.readDoc(reader);
            for (Doc d : parsedDoc)
            {
                Document doc = new Document();

                // Adds the path of the file as a field named "path".  
                Field pathField = new StringField("path", file.toString(), Field.Store.YES);
                doc.add(pathField);

                doc.add(new StringField("docno", d.docno, Field.Store.YES));

                // Adds the last modified date of the file a field named "modified".
               
                doc.add(new LongPoint("modified", lastModified));

                // Adds the contents of the file to a field named "contents".
                FieldType type = new FieldType();
                type.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
                type.setStored(true);
                type.setStoreTermVectors(true);

//                doc.add(new TextField("contents", d.content, Field.Store.YES));
                doc.add(new Field("contents", d.content, type));

                if (writer.getConfig().getOpenMode() == IndexWriterConfig.OpenMode.CREATE)
                {
                    // New index, so we just add the document (no old document can be there):
                    System.out.println("adding " + file + " " + d.docno);
                    writer.addDocument(doc);
                }
                else
                {
                    // Existing index 
                    System.out.println("updating " + file + " " + d.docno);
                    writer.updateDocument(new Term("path", file.toString()), doc);
                }
            }
        }
    }
}
