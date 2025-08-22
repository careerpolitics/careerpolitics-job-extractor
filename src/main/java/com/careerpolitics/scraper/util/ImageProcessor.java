package com.careerpolitics.scraper.util;


import org.imgscalr.Scalr;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

@Component
public class ImageProcessor {

    public byte[] resizeAndCompress(byte[] imageBytes, int maxWidth, int qualityPercent) throws IOException {
        BufferedImage input = ImageIO.read(new ByteArrayInputStream(imageBytes));
        if (input == null) return imageBytes;

        int width = input.getWidth();
        BufferedImage toWrite = input;
        if (width > maxWidth) {
            toWrite = Scalr.resize(input, Scalr.Method.QUALITY, Scalr.Mode.FIT_TO_WIDTH, maxWidth);
        }
        float quality = Math.max(0.2f, Math.min(1.0f, qualityPercent / 100f));
        return writeJpeg(toWrite, quality);
    }

    private byte[] writeJpeg(BufferedImage img, float quality) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            ImageIO.write(img, "jpg", baos);
            return baos.toByteArray();
        }
        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
        }
        writer.setOutput(new MemoryCacheImageOutputStream(baos));
        writer.write(null, new IIOImage(img, null, null), param);
        writer.dispose();
        return baos.toByteArray();
    }
}
