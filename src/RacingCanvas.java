import javax.microedition.lcdui.*;
import java.util.*;

public class RacingCanvas extends Canvas implements Runnable {

    // Constantes
    static final int WINDOW_WIDTH = 240;
    static final int WINDOW_HEIGHT = 320;

    static final int ROAD_WIDTH = 2000;
    static final int SEGMENT_LEN = 200;
    static final float CAM_DEPTH = 0.84f;
    static final int SHOW_N_SEG = 80;  // reduzido para performance

    // Cores
    static final int DARK_GRASS = 0x009A00;
    static final int LIGHT_GRASS = 0x10C810;
    static final int WHITE_RUMBLE = 0xFFFFFF;
    static final int BLACK_RUMBLE = 0x000000;
    static final int DARK_ROAD = 0x696969;
    static final int LIGHT_ROAD = 0x6B6B6B;

    // Física do carro
    static final float MAX_SPEED = 2000f;
    static final float ACCEL = 1500f;
    static final float BRAKE = 2000f;
    static final float DECEL = 500f;

    // Classe Line interna
    class Line {
        public int i;
        public float x, y, z;
        public float X, Y, W;
        public float scale;
        public float curve;
        public float spriteX;
        public float clip;

        public int grassColor;
        public int rumbleColor;
        public int roadColor;

        public int spriteIndex;

        public Line(int index) {
            this.i = index;
            this.x = this.y = this.z = 0.0f;
            this.X = this.Y = this.W = 0.0f;
            this.scale = 0.0f;
            this.curve = 0.0f;
            this.spriteX = 0.0f;
            this.clip = 0.0f;
            this.spriteIndex = -1;
        }

        public void project(int camX, int camY, int camZ, int scrW, int scrH, float camD) {
            float zDiff = this.z - camZ;
            if (zDiff < 1) zDiff = 1;

            this.scale = camD / zDiff;
            this.X = (1 + this.scale * (this.x - camX)) * scrW / 2;
            this.Y = (1 - this.scale * (this.y - camY)) * scrH / 2;
            this.W = this.scale * ROAD_WIDTH * scrW / 2;
        }
    }

    // Arrays
    private Line[] lines;

    // Estado do jogo
    private boolean running;
    private Thread thread;

    private float playerX = 0;
    private float playerY = 1500;
    private float pos = 0;

    private int scrW, scrH;
    private Image offScreen;
    private Graphics offG;

    // Controles (agora com todos declarados)
    private boolean keyUp = false;
    private boolean keyDown = false;
    private boolean keyLeft = false;
    private boolean keyRight = false;
    private boolean keyTab = false;

    public RacingCanvas() {
        scrW = getWidth();
        scrH = getHeight();

        buildTrack();
    }

    private void buildTrack() {
        lines = new Line[2000];  // mais segmentos para um circuito longo

        // Parâmetros do circuito
        float y = 0;  // altura acumulada

        for (int i = 0; i < lines.length; i++) {
            lines[i] = new Line(i);
            lines[i].z = i * SEGMENT_LEN;

            // CORES - alternam a cada 5 segmentos
            boolean isLight = ((i / 5) % 2 == 0);
            lines[i].grassColor = isLight ? LIGHT_GRASS : DARK_GRASS;
            lines[i].rumbleColor = isLight ? WHITE_RUMBLE : BLACK_RUMBLE;
            lines[i].roadColor = isLight ? LIGHT_ROAD : DARK_ROAD;

            // ==========================================
            // TRECHO 1: RETA INICIAL (segmentos 0-300)
            // ==========================================
            if (i < 300) {
                lines[i].curve = 0;      // reta
                y += 0;                   // plano
            }

            // ==========================================
            // TRECHO 2: CURVA SUAVE À DIREITA (300-500)
            // ==========================================
            else if (i < 500) {
                // Curva progressiva (começa suave, fica mais forte)
                float t = (i - 300) / 200.0f;  // 0 a 1
                lines[i].curve = 0.3f + t * 0.5f;  // 0.3 até 0.8

                // Pequena elevação
                y += 2;
            }

            // ==========================================
            // TRECHO 3: SUBIDA (500-700)
            // ==========================================
            else if (i < 700) {
                lines[i].curve = 0;      // reta

                // Subida íngreme
                y += 5;
            }

            // ==========================================
            // TRECHO 4: CURVA FORTE À ESQUERDA (700-900)
            // ==========================================
            else if (i < 900) {
                float t = (i - 700) / 200.0f;
                lines[i].curve = -0.5f - t * 0.8f;  // -0.5 até -1.3

                // Descida
                y -= 3;
            }

            // ==========================================
            // TRECHO 5: MORRO (900-1200)
            // ==========================================
            else if (i < 1200) {
                lines[i].curve = 0;

                // Seno para criar um morro
                float angulo = (i - 900) * 0.03f;
                y += (float)Math.sin(angulo) * 8;
            }

            // ==========================================
            // TRECHO 6: "S" (1200-1500)
            // ==========================================
            else if (i < 1500) {
                float t = (i - 1200) / 300.0f;
                // Alterna entre esquerda e direita
                lines[i].curve = (float)Math.sin(t * Math.PI * 2) * 1.5f;

                y += (float)Math.sin(t * Math.PI) * 2;
            }

            // ==========================================
            // TRECHO 7: VOLTA À RETA (1500-1700)
            // ==========================================
            else if (i < 1700) {
                lines[i].curve = 0;
                y += 0;
            }

            // ==========================================
            // TRECHO 8: CURVA FINAL (1700-1900)
            // ==========================================
            else if (i < 1900) {
                lines[i].curve = 0.8f;
                y -= 2;
            }

            // ==========================================
            // TRECHO 9: RETA FINAL (1900-2000)
            // ==========================================
            else {
                lines[i].curve = 0;
                y += 0;
            }

            // Aplica a altura acumulada
            lines[i].y = y;

            // ==========================================
            // SPRITES (DECORAÇÕES)
            // ==========================================
            // Árvores na beira da estrada
            if (i % 15 == 0 && i > 100) {
                lines[i].spriteIndex = (i % 3) + 1;  // sprites 1,2,3
                lines[i].spriteX = (i % 2 == 0) ? -2.5f : 2.5f;  // alterna lados
            }

            // Placas de indicação
            if (i == 350 || i == 750 || i == 1250) {
                lines[i].spriteIndex = 4;  // sprite de placa
                lines[i].spriteX = 1.8f;
            }

            // Túnel (apenas para testar)
            if (i > 800 && i < 850) {
                lines[i].y = y - 500;  // rebaixa o teto (simula túnel)
            }
        }
    }

    public void start() {
        running = true;
        thread = new Thread(this);
        thread.start();
    }

    public void stop() {
        running = false;
        try {
            if (thread != null) {
                thread.join();
            }
        } catch (Exception e) {}
    }

    public void run() {
        long lastTime = System.currentTimeMillis();

        while (running) {
            long now = System.currentTimeMillis();
            int dt = (int)(now - lastTime);
            lastTime = now;

            if (dt > 100) dt = 100;  // limite máximo

            update(dt);
            repaint();

            try { Thread.sleep(30); } catch (Exception e) {}
        }
    }

    private void update(int dt) {
        float speed = 0;
        float dtSec = dt / 1000.0f;

        // Segmento atual
        int currentSeg = (int)(pos / SEGMENT_LEN) % lines.length;
        Line seg = lines[currentSeg];

        // ==========================================
        // EFEITO DAS COLINAS NA VELOCIDADE
        // ==========================================
        float inclinacao = 0;
        if (currentSeg > 0) {
            Line prevSeg = lines[(currentSeg - 1 + lines.length) % lines.length];
            inclinacao = (seg.y - prevSeg.y) / SEGMENT_LEN;  // inclinação atual
        }

        // Aceleração/desaceleração nas colinas
        if (keyUp) {
            speed += ACCEL * dtSec;
        } else if (keyDown) {
            speed -= BRAKE * dtSec;
        } else {
            speed -= DECEL * dtSec;
        }

        // Gravidade nas subidas/descidas
        speed -= inclinacao * 500 * dtSec;  // perde velocidade na subida

        // ==========================================
        // CURVAS AFETAM A DIREÇÃO
        // ==========================================
        float steerInput = 0;
        if (keyLeft) steerInput = -1;
        if (keyRight) steerInput = 1;

        // A direção é menos responsiva em alta velocidade
        float steerFactor = 1.0f - (speed / MAX_SPEED) * 0.5f;
        playerX += steerInput * 400 * dtSec * steerFactor;

        // Força centrífuga (maior em curvas fechadas)
        playerX += seg.curve * (speed * speed) * 0.0001f * dtSec;

        // ==========================================
        // CÂMERA ACOMPANHA A ALTURA DA PISTA
        // ==========================================
        playerY = 1500 + seg.y * 0.5f;  // câmera sobe com a pista

        // Limites
        if (playerY < 500) playerY = 500;
        if (playerY > 3000) playerY = 3000;

        // Avança
        pos += speed * dtSec * 100;  // ajuste fino da velocidade
        if (pos < 0) pos = 0;

        // Loop da pista
        int N = lines.length;
        while (pos >= N * SEGMENT_LEN) {
            pos -= N * SEGMENT_LEN;
        }
    }

    // IMPLEMENTAÇÃO OBRIGATÓRIA DO MÉTODO paint
    protected void paint(Graphics g) {
        if (offScreen == null) {
            offScreen = Image.createImage(scrW, scrH);
            offG = offScreen.getGraphics();
        }

        // Limpa tela
        offG.setColor(0x69CD04);
        offG.fillRect(0, 0, scrW, scrH);

        int N = lines.length;
        int startPos = (int)(pos / SEGMENT_LEN);
        if (startPos >= N) startPos = N - 1;

        float x = 0, dx = 0;
        int camH = (int)(lines[startPos].y + playerY);
        float maxY = scrH;

        // Projeta segmentos
        for (int n = startPos; n < startPos + SHOW_N_SEG; n++) {
            int idx = n % N;
            Line current = lines[idx];

            int camZ = (int)(pos - (n >= N ? N * SEGMENT_LEN : 0));
            current.project((int)(playerX - x), camH, camZ, scrW, scrH, CAM_DEPTH);

            x += dx;
            dx += current.curve;

            current.clip = maxY;

            if (current.Y >= maxY) continue;
            maxY = current.Y;

            int prevIdx = (idx - 1 + N) % N;
            Line prev = lines[prevIdx];

            // Desenha grama
            drawQuad(offG, current.grassColor,
                    0, (int)prev.Y, scrW,
                    0, (int)current.Y, scrW);

            // Desenha acostamento
            drawQuad(offG, current.rumbleColor,
                    (int)prev.X, (int)prev.Y, (int)(prev.W * 1.2f),
                    (int)current.X, (int)current.Y, (int)(current.W * 1.2f));

            // Desenha pista
            drawQuad(offG, current.roadColor,
                    (int)prev.X, (int)prev.Y, (int)prev.W,
                    (int)current.X, (int)current.Y, (int)current.W);
        }

        // Desenha HUD
        drawHUD(offG);

        // Desenha na tela
        g.drawImage(offScreen, 0, 0, Graphics.TOP | Graphics.LEFT);
    }

    private void drawQuad(Graphics g, int color, int x1, int y1, int w1,
                          int x2, int y2, int w2) {
        g.setColor(color);

        int yStart = Math.min(y1, y2);
        int yEnd = Math.max(y1, y2);

        if (yStart < 0) yStart = 0;
        if (yEnd > scrH) yEnd = scrH;

        for (int y = yStart; y < yEnd; y++) {
            float t = (float)(y - yStart) / (yEnd - yStart + 1);
            int xLeft = (int)((x1 - w1) * (1 - t) + (x2 - w2) * t);
            int xRight = (int)((x1 + w1) * (1 - t) + (x2 + w2) * t);

            if (xLeft < 0) xLeft = 0;
            if (xRight >= scrW) xRight = scrW - 1;
            if (xRight > xLeft) {
                g.drawLine(xLeft, y, xRight, y);
            }
        }
    }

    private void drawHUD(Graphics g) {
        g.setColor(0xFFFFFF);
        g.setFont(Font.getDefaultFont());
        g.drawString("Pos: " + (int)pos, 5, 5, Graphics.TOP | Graphics.LEFT);
        g.drawString("X: " + (int)playerX, 5, 20, Graphics.TOP | Graphics.LEFT);
    }

    // Controles
    protected void keyPressed(int keyCode) {
        int gameAction = getGameAction(keyCode);

        if (gameAction == UP || keyCode == KEY_NUM8) keyUp = true;
        if (gameAction == DOWN || keyCode == KEY_NUM5) keyDown = true;
        if (gameAction == LEFT || keyCode == KEY_NUM4) keyLeft = true;
        if (gameAction == RIGHT || keyCode == KEY_NUM6) keyRight = true;
        if (keyCode == KEY_NUM2) keyTab = true;  // Turbo no 2

        // Para depuração
        System.out.println("Key pressed: " + keyCode);
    }

    protected void keyReleased(int keyCode) {
        int gameAction = getGameAction(keyCode);

        if (gameAction == UP || keyCode == KEY_NUM8) keyUp = false;
        if (gameAction == DOWN || keyCode == KEY_NUM5) keyDown = false;
        if (gameAction == LEFT || keyCode == KEY_NUM4) keyLeft = false;
        if (gameAction == RIGHT || keyCode == KEY_NUM6) keyRight = false;
        if (keyCode == KEY_NUM2) keyTab = false;
    }
}