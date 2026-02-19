import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;

public class RacingMIDlet extends MIDlet {

    private RacingCanvas canvas;
    private Display display;

    public RacingMIDlet() {
        canvas = new RacingCanvas();  // SEM parâmetros
        display = Display.getDisplay(this);
    }

    public void startApp() {
        display.setCurrent(canvas);
        canvas.start();
    }

    public void pauseApp() {
        canvas.stop();
    }

    public void destroyApp(boolean unconditional) {
        canvas.stop();
    }

    public void exit() {
        destroyApp(true);
        notifyDestroyed();
    }
}