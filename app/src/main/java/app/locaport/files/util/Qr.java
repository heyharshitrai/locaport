package app.locaport.files.util;

import android.graphics.Bitmap;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.EnumMap;
import java.util.Map;

public final class Qr {

    private Qr() {
    }

    public static Bitmap encode(String text, int sizePx) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.MARGIN, 1);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, hints);
            int[] pixels = new int[sizePx * sizePx];
            for (int y = 0; y < sizePx; y++) {
                int offset = y * sizePx;
                for (int x = 0; x < sizePx; x++) {
                    pixels[offset + x] = matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF;
                }
            }
            Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx);
            return bitmap;
        } catch (Exception e) {
            return null;
        }
    }
}
