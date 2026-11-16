import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Launcher icons for a factory app: a rounded square with a two-tone gradient picked from the app's name, a soft
 * reactor ring and the name's first letter. Run: java factory/IconMaker.java "App Name" factory/app/src/main/res
 */
public class IconMaker {
    static final int[][] PALETTES = {
        {0x00B8D4, 0x304FFE}, {0xFF6F00, 0xD50000}, {0x00C853, 0x00796B},
        {0xAA00FF, 0x304FFE}, {0xFF4081, 0x7C4DFF}, {0xFFC400, 0xFF6D00},
    };

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        String name = args[0].trim();
        int[] pal = PALETTES[Math.floorMod(name.toLowerCase().hashCode(), PALETTES.length)];
        String letter = "J";
        for (char ch : name.toCharArray()) if (Character.isLetterOrDigit(ch)) { letter = String.valueOf(Character.toUpperCase(ch)); break; }
        String[][] sizes = {{"mdpi", "48"}, {"hdpi", "72"}, {"xhdpi", "96"}, {"xxhdpi", "144"}, {"xxxhdpi", "192"}};
        for (String[] s : sizes) {
            int px = Integer.parseInt(s[1]);
            BufferedImage img = new BufferedImage(px, px, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Shape body = new RoundRectangle2D.Float(0, 0, px, px, px * 0.44f, px * 0.44f);
            g.setPaint(new GradientPaint(0, 0, new Color(pal[0]), px, px, new Color(pal[1])));
            g.fill(body);
            g.setClip(body);
            g.setColor(new Color(255, 255, 255, 70));
            g.setStroke(new BasicStroke(px * 0.025f));
            float r = px * 0.30f;
            g.draw(new Ellipse2D.Float(px / 2f - r, px / 2f - r, 2 * r, 2 * r));
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.round(px * 0.46f)));
            FontMetrics fm = g.getFontMetrics();
            int x = (px - fm.stringWidth(letter)) / 2;
            int y = (px - fm.getHeight()) / 2 + fm.getAscent();
            g.drawString(letter, x, y);
            g.dispose();
            File dir = new File(args[1], "mipmap-" + s[0]);
            dir.mkdirs();
            ImageIO.write(img, "png", new File(dir, "ic_launcher.png"));
        }
    }
}
