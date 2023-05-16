# Probabilistic-Ranking
Implementation of  probabilistic ranking with basic smoothing, and blind relevance feedback

# Instructions :

Please ensure that Java is up to date and all files are within the same directory 


To run the jar file, please follow the following commands : 

java -jar HW1.jar [Method ]  index topics.351-400 output.txt

Where method can be the following : 
- BM25
- LMDirichlet
- LMJelinekMercer
- RM1
- RM3


Example: 

java -jar HW1.jar BM25  index topics.351-400 outputBM25.txt 

java -jar HW1.jar LMDirichlet index topics.351-400 outputLMDirichlet.txt 

java -jar HW1.jar LMJelinekMercer index topics.351-400 outputLMJelinekMercer.txt

java -jar HW1.jar RM1 index topics.351-400 outputRM1.txt

java -jar HW1.jar RM3 index topics.351-400 outputRM3.txt


trec_eval.linux Example commands : 

java -jar HW1.jar BM25  index trec_eval.linux resultbm25.txt

java -jar HW1.jar LMDirichlet index trec_eval.linux resultLMDirichlet.txt

java -jar HW1.jar LMJelinekMercer index trec_eval.linux resultLMDirichlet.txt

java -jar HW1.jar RM1 index trec_eval.linux resultRM1.txt

java -jar HW1.jar RM3 index trec_eval.linux resultRM2.txt












