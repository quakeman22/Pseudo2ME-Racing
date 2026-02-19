public class Line {
    public int i;
    public float x, y, z;        // posição 3D
    public float X, Y, W;         // projeção 2D
    public float scale;
    public float curve;
    public float spriteX;
    public float clip;

    // Cores (armazenadas como ints RGB)
    public int grassColor;
    public int rumbleColor;
    public int roadColor;

    // Controle de sprite
    public int spriteIndex;       // -1 = sem sprite
    public int spriteXpos;        // posição X do sprite na pista

    public Line(int index) {
        this.i = index;
        this.x = this.y = this.z = 0.0f;
        this.X = this.Y = this.W = 0.0f;
        this.scale = 0.0f;
        this.curve = 0.0f;
        this.spriteX = 0.0f;
        this.clip = 0.0f;
        this.spriteIndex = -1;
        this.spriteXpos = 0;
    }

    public void project(int camX, int camY, int camZ, int scrW, int scrH, float camD) {
        // Evitar divisão por zero
        float zDiff = this.z - camZ;
        if (zDiff < 1) zDiff = 1;

        this.scale = camD / zDiff;
        this.X = (1 + this.scale * (this.x - camX)) * scrW / 2;
        this.Y = (1 - this.scale * (this.y - camY)) * scrH / 2;
        this.W = this.scale * RacingCanvas.ROAD_WIDTH * scrW / 2;
    }
}