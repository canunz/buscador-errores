public class Hold {
    private static native void privateProcess();
    public static void main(String[] args) throws Exception {
        System.load("/fixture/libnodump.so");
        privateProcess();
        synchronized (Hold.class) { Hold.class.wait(); }
    }
}
