# Demo artwork

`generate.py` draws every panel, frame and tooltip the demo ships. Run it from this directory:

```
python3 generate.py
```

The PNGs it writes are checked in, so the demo runs from a clone without Python.

## Why `vanilla/` is not in the repository

The generator reads raw RGBA dumps from `vanilla/`, and those are pixels this repository is not
allowed to redistribute: `button`, `button_highlighted`, `button_disabled` and `ascii` come out of
the Minecraft client, and `gicon_*` out of the grounds icon set. They are in `.gitignore` for that
reason. Reading the client's actual pixels rather than approximating them is the whole reason the
buttons look native instead of merely Minecraft-ish — but that is a local build step, not a
distribution.

Recreating them needs a JDK and takes a minute. Each dump is `width:int32`, `height:int32`, then
`width * height` ARGB `int32` values, big-endian.

```java
// Dump.java — run once per source image
import javax.imageio.ImageIO;
import java.io.*;
import java.awt.image.BufferedImage;

public class Dump {
    public static void main(String[] args) throws Exception {
        // args: <source.png> <name>   ->  writes vanilla/<name>.rgba
        BufferedImage image = ImageIO.read(new File(args[0]));
        try (DataOutputStream out =
                new DataOutputStream(new BufferedOutputStream(
                    new FileOutputStream("vanilla/" + args[1] + ".rgba")))) {
            out.writeInt(image.getWidth());
            out.writeInt(image.getHeight());
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    out.writeInt(image.getRGB(x, y));
                }
            }
        }
    }
}
```

Sources, all from an unpacked 26.2 client under `assets/minecraft/textures/`:

| dump | source |
| --- | --- |
| `button` | `gui/sprites/widget/button.png` |
| `button_highlighted` | `gui/sprites/widget/button_highlighted.png` |
| `button_disabled` | `gui/sprites/widget/button_disabled.png` |
| `ascii` | `font/ascii.png` |
| item icons | `item/<name>.png` |
| `gicon_<name>` | the grounds icon set, one file per icon |
