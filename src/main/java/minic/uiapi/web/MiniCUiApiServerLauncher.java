package minic.uiapi.web;

/**
 * Command-line launcher for the browser UIAPI HTTP service.
 */
public final class MiniCUiApiServerLauncher {
    private MiniCUiApiServerLauncher() {
    }

    public static void main(String[] args) throws Exception {
        int port = args.length == 0 ? 18080 : Integer.parseInt(args[0]);
        MiniCUiApiServer server = MiniCUiApiServer.create(port);
        Runtime.getRuntime().addShutdownHook(new Thread(server::close, "minic-uiapi-shutdown"));
        server.start();
        System.out.println("MiniC UIAPI HTTP server listening at " + server.baseUri());
        Thread.currentThread().join();
    }
}
