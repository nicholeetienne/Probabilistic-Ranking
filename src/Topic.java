public class Topic
{
    String title;
    String desc;
    String number;

    String getQuery()
    {
        String query = title + "\n" + desc;
        query = query.replace("/", " ")
                .replace("(", " ").replace(")", " ")
                .replace("?", " ").replace('"', ' ');
        return query;
    }
}
