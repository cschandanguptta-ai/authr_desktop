import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.lang.reflect.Method;

/**
 * P3 headless UI walkthrough: launches the app, completes onboarding, then
 * drives navigation through the Writer Studio (tab 6) and the new Serial
 * Dashboard (tab 7), capturing screenshots.
 */
public class SmokeShotP3 {
    public static void main(String[] args) throws Exception {
        String outDir = System.getProperty("shot.out", "/tmp/render");
        new File(outDir).mkdirs();

        Thread app = new Thread(() -> {
            try {
                Class<?> main = Class.forName("com.example.MainKt");
                main.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            } catch (Throwable t) {
                System.err.println("APP CRASHED: " + t);
                Throwable cause = t.getCause() != null ? t.getCause() : t;
                cause.printStackTrace();
            }
        }, "authr-app");
        app.start();

        Thread.sleep(15000); // wait for first composition
        screenshot(new File(outDir, "p3-01-onboarding.png"));

        // Drive the real app state through Koin + the StudioViewModel
        Class<?> gc = Class.forName("org.koin.core.context.GlobalContext");
        Object globalContext = gc.getField("INSTANCE").get(null);
        Object koin = gc.getMethod("get").invoke(globalContext);
        Method getVm = null;
        for (Method candidate : koin.getClass().getMethods()) {
            if (candidate.getName().equals("get") && candidate.getParameterCount() == 3
                    && candidate.getParameterTypes()[0].getName().equals("kotlin.reflect.KClass")) {
                getVm = candidate;
                break;
            }
        }
        if (getVm == null) throw new NoSuchMethodException("Koin.get(KClass, Qualifier, Parameters)");
        getVm.setAccessible(true);
        Class<?> jvmClassMapping = Class.forName("kotlin.jvm.JvmClassMappingKt");
        Method getKotlinClass = null;
        for (Method m : jvmClassMapping.getMethods()) {
            if (m.getName().equals("getKotlinClass") && m.getParameterCount() == 1
                    && m.getParameterTypes()[0] == Class.class) {
                getKotlinClass = m;
                break;
            }
        }
        getKotlinClass.setAccessible(true);
        Object studioVmClass = getKotlinClass.invoke(null, Class.forName("com.example.ui.viewmodel.StudioViewModel"));
        Object studioVm = getVm.invoke(koin, studioVmClass, null, null);

        instanceCall(studioVm, "completeOnboarding", null);
        Thread.sleep(3000);
        screenshot(new File(outDir, "p3-02-library.png"));

        // Writer Studio (tab 6, P1 surface — regression check)
        instanceCall(studioVm, "selectTab", new Object[]{6});
        Thread.sleep(4000);
        screenshot(new File(outDir, "p3-03-writer-studio.png"));

        // Back to Story, then the Serial Dashboard (tab 7, the P3 surface)
        instanceCall(studioVm, "selectTab", new Object[]{0});
        Thread.sleep(2500);
        instanceCall(studioVm, "selectTab", new Object[]{7});
        Thread.sleep(4500);
        screenshot(new File(outDir, "p3-04-serial-dashboard.png"));

        // Settings (regression)
        instanceCall(studioVm, "selectTab", new Object[]{5});
        Thread.sleep(3000);
        screenshot(new File(outDir, "p3-05-settings.png"));

        System.out.println("P3 SMOKE OK");
        System.exit(0);
    }

    private static Object instanceCall(Object target, String method, Object[] params) throws Exception {
        Class<?> c = target.getClass();
        Method m = null;
        for (Method candidate : c.getMethods()) {
            if (candidate.getName().equals(method) && candidate.getParameterCount() == (params == null ? 0 : params.length)) {
                m = candidate; break;
            }
        }
        if (m == null) throw new NoSuchMethodException(c.getName() + "." + method);
        m.setAccessible(true);
        return params == null ? m.invoke(target) : m.invoke(target, params);
    }

    private static void screenshot(File target) throws Exception {
        Robot robot = new Robot();
        Rectangle screen = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        BufferedImage img = robot.createScreenCapture(screen);
        ImageIO.write(img, "png", target);
        System.out.println("Saved screenshot: " + target.getAbsolutePath());
    }
}
