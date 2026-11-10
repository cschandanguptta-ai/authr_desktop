import java.lang.reflect.Method;
import java.util.List;

/** Report evidence: golden serial word counts + suite verdict, via reflection on the built jar. */
public class CorpusProbe {
    public static void main(String[] args) throws Exception {
        Class<?> gs = Class.forName("com.example.engine.serialization.GoldenSerial");
        Class<?> ov = Class.forName("com.example.engine.serialization.OpeningValidators");
        Class<?> bookC = Class.forName("com.example.data.model.Book");
        Object gsInstance = gs.getField("INSTANCE").get(null);
        Object book = gs.getMethod("book").invoke(gsInstance);
        Method chapters = gs.getMethod("chapters$default", gs, int.class, int.class, int.class, Object.class);
        Object chs = chapters.invoke(null, gsInstance, 901, 11, 3, null);
        List<?> list = (List<?>) chs;
        Method wc = ov.getMethod("wordCount", String.class);
        Method content = bookC.getMethod("getTitle");
        System.out.println("book.title = " + content.invoke(book));
        int min = Integer.MAX_VALUE, max = 0;
        for (Object ch : list) {
            Class<?> cc = ch.getClass();
            String text = (String) cc.getMethod("getContent").invoke(ch);
            int n = (Integer) cc.getMethod("getChapterNumber").invoke(ch);
            int words = (Integer) wc.invoke(null, text);
            min = Math.min(min, words); max = Math.max(max, words);
            String ending = (String) cc.getMethod("getEndingType").invoke(ch);
            System.out.println("ch" + n + ": " + words + " words, ending " + ending + ", released=" + cc.getMethod("getStatus").invoke(ch));
        }
        System.out.println("envelope: " + min + ".." + max + " words (book envelope 1000..2000)");
    }
}
