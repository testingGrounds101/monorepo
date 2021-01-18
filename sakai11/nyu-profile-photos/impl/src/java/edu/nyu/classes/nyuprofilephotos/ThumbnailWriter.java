package edu.nyu.classes.nyuprofilephotos;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.*;
import javax.imageio.ImageIO;

import org.sakaiproject.component.cover.HotReloadConfigurationService;

class ThumbnailWriter {
    private final String outputDir;
    private final int thumbnailSize;

    public ThumbnailWriter() {
        this.outputDir = HotReloadConfigurationService.getString("profile-photos.thumbnail-dir", "");
        this.thumbnailSize = Integer.parseInt(HotReloadConfigurationService.getString("profile-photos.thumbnail-size", "80"));

        if (this.outputDir.isEmpty()) {
            throw new RuntimeException("Thumbnail output dir not set");
        }

        if (this.thumbnailSize <= 0) {
            throw new RuntimeException("Failed to parse thumbnail size");
        }
    }

    private OutputStream openOutput(File outfile) throws Exception {
        outfile.getParentFile().mkdirs();
        return new FileOutputStream(outfile);
    }

    private File fileFor(String netid, String suffix) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        digest.update(netid.getBytes("UTF-8"));
        byte[] hash = digest.digest();

        Path outpath = Paths.get(this.outputDir,
                                 String.format("%02x", hash[0] & 0xff),
                                 String.format("%02x", hash[1] & 0xff),
                                 netid + ".png");

        String pathStr = outpath.toString();

        if (suffix != null) {
            pathStr += "." + suffix;
        }

        return new File(pathStr);
    }

    public boolean hasThumbnail(String netid) throws Exception {
        boolean result = fileFor(netid, null).exists();

        System.err.println(String.format("** File '%s' exists? %s", fileFor(netid, null).getPath(), result));

        return result;
    }

    public void generateThumbnail(String netid, byte[] jpegBytes) throws Exception {
        File tempFile = fileFor(netid.toLowerCase(Locale.ROOT), "tmp");
        File finalFile = fileFor(netid.toLowerCase(Locale.ROOT), null);

        try (OutputStream out = openOutput(tempFile)) {
            BufferedImage sourceImage = ImageIO.read(new ByteArrayInputStream(jpegBytes));

            BufferedImage thumbnail = thumbnailForImage(sourceImage);
            ImageIO.write(thumbnail, "png", out);
        }

        System.err.println(String.format("Producing file: %s", finalFile.getPath()));

        tempFile.renameTo(finalFile);
    }

    private BufferedImage thumbnailForImage(BufferedImage source) {
        int originalWidth = source.getWidth();
        int originalHeight = source.getHeight();

        int newWidth;
        int newHeight;

        if (originalWidth > originalHeight) {
            float aspect = originalWidth / (float)this.thumbnailSize;
            newWidth = this.thumbnailSize;
            newHeight = (int)Math.floor(originalHeight / aspect);
        } else {
            float aspect = originalHeight / (float)this.thumbnailSize;
            newHeight = this.thumbnailSize;
            newWidth = (int)Math.floor(originalWidth / aspect);
        }

        java.awt.Image scaledImage = source.getScaledInstance(newWidth, newHeight, java.awt.Image.SCALE_SMOOTH);
        BufferedImage result = new BufferedImage(this.thumbnailSize,
                                                 this.thumbnailSize,
                                                 BufferedImage.TYPE_INT_ARGB);

        Graphics g = result.createGraphics();

        try {
            int x = (int)Math.floor((this.thumbnailSize - newWidth) / 2.0);
            int y = (int)Math.floor((this.thumbnailSize - newHeight) / 2.0);

            g.drawImage(scaledImage, x, y, null);
        } finally {
            g.dispose();
        }

        // Increase the contrast to avoid images looking washed out...
        RescaleOp fixContrast = new RescaleOp(new float[] { 1.2f, 1.2f, 1.2f, 1.0f },
                                              new float[] { -32f, -32f, -32f, 0f },
                                              null);

        fixContrast.filter(result, result);

        return result;
    }
}
