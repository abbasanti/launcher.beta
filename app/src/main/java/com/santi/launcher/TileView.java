package com.santi.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

/** Dibuja el logo de una app dentro de un cuadrado de esquinas redondeadas. */
class TileView extends View {

    private final Drawable icono;
    private final float escala;
    private final int colorFondo;
    private final float proporcion;
    private final Paint pintura = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path recorte = new Path();
    private final RectF area = new RectF();

    /** proporcion: parte del cuadrado que ocupa un ícono no adaptable. */
    TileView(Context contexto, Drawable icono, float escala, int colorFondo, float proporcion) {
        super(contexto);
        this.icono = icono;
        this.escala = escala;
        this.colorFondo = colorFondo;
        this.proporcion = proporcion;
        setFocusable(true);
        setClickable(true);
    }

    @Override
    protected void onDraw(Canvas lienzo) {
        float radio = getWidth() * 0.12f;
        area.set(0, 0, getWidth(), getHeight());
        recorte.reset();
        recorte.addRoundRect(area, radio, radio, Path.Direction.CW);

        lienzo.save();
        lienzo.clipPath(recorte);
        pintura.setStyle(Paint.Style.FILL);
        pintura.setColor(colorFondo);
        lienzo.drawRect(area, pintura);

        if (!dibujarAdaptable(lienzo)) {
            // Ícono tradicional: centrado, sin deformar.
            int ancho = Math.max(1, icono.getIntrinsicWidth());
            int alto = Math.max(1, icono.getIntrinsicHeight());
            float factor = Math.min(getWidth() * proporcion / ancho, getHeight() * proporcion / alto);
            int w = Math.round(ancho * factor);
            int h = Math.round(alto * factor);
            int x = (getWidth() - w) / 2;
            int y = (getHeight() - h) / 2;
            icono.setBounds(x, y, x + w, y + h);
            icono.draw(lienzo);
        }
        lienzo.restore();

        if (isFocused()) {
            float grosor = 4 * escala;
            area.inset(grosor / 2, grosor / 2);
            pintura.setStyle(Paint.Style.STROKE);
            pintura.setStrokeWidth(grosor);
            pintura.setColor(Tema.ACENTO);
            lienzo.drawRoundRect(area, radio, radio, pintura);
        }
    }

    /**
     * Los íconos adaptables (Android 8+) tienen fondo y frente separados, así
     * que pueden ocupar todo el cuadrado en lugar de quedar redondos.
     */
    private boolean dibujarAdaptable(Canvas lienzo) {
        try {
            Class<?> tipo = Class.forName("android.graphics.drawable.AdaptiveIconDrawable");
            if (!tipo.isInstance(icono)) {
                return false;
            }
            Drawable fondo = (Drawable) tipo.getMethod("getBackground").invoke(icono);
            Drawable frente = (Drawable) tipo.getMethod("getForeground").invoke(icono);
            // Las capas miden 108 unidades y el área visible 72.
            int extraX = getWidth() / 4;
            int extraY = getHeight() / 4;
            for (Drawable capa : new Drawable[]{fondo, frente}) {
                if (capa != null) {
                    capa.setBounds(-extraX, -extraY, getWidth() + extraX, getHeight() + extraY);
                    capa.draw(lienzo);
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
