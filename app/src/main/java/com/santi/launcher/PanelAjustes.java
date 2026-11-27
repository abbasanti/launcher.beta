package com.santi.launcher;

import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Panel lateral con los ajustes del launcher. */
final class PanelAjustes {

    private static final String[] TEMAS = {"Según el sistema", "Claro", "Oscuro"};
    private static final String[] ATENUADOS = {"Sin atenuar", "Suave", "Fuerte"};
    private static final String[] TAMANOS = {"Chico", "Mediano", "Grande"};
    private static final String[] NOMBRES = {"Solo al seleccionar", "Siempre"};
    private static final String[] HORAS = {"24 horas", "12 horas"};
    private static final String[] SI_NO = {"Mostrar", "Ocultar"};

    private final MainActivity app;
    private final Config config;
    private final Tema tema;
    private final List<View> filas = new ArrayList<View>();
    private LinearLayout columna;

    private PanelAjustes(MainActivity app, Config config, Tema tema) {
        this.app = app;
        this.config = config;
        this.tema = tema;
    }

    /** Construye el panel y enfoca la fila indicada. */
    static View crear(MainActivity app, Config config, Tema tema, int filaEnfocada) {
        PanelAjustes panel = new PanelAjustes(app, config, tema);
        View vista = panel.construir();
        final View foco = panel.filas.get(Math.max(0, Math.min(filaEnfocada, panel.filas.size() - 1)));
        foco.post(new Runnable() {
            @Override
            public void run() {
                foco.requestFocus();
            }
        });
        return vista;
    }

    private View construir() {
        ScrollView desplazable = new ScrollView(app);
        desplazable.setBackgroundColor(tema.panel);
        desplazable.setVerticalScrollBarEnabled(false);
        // Evita que los toques pasen a la pantalla de atrás.
        desplazable.setClickable(true);

        columna = new LinearLayout(app);
        columna.setOrientation(LinearLayout.VERTICAL);
        columna.setPadding(app.px(24), app.px(28), app.px(24), app.px(28));
        desplazable.addView(columna);

        TextView titulo = etiqueta("Ajustes del launcher", 26, tema.texto);
        titulo.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        titulo.setPadding(app.px(16), 0, 0, app.px(8));
        columna.addView(titulo);

        seccion("Apariencia");
        fila("Fondo de pantalla", Fondos.NOMBRES[limitar(config.numero(Config.FONDO, 0), Fondos.NOMBRES)],
                new Runnable() {
                    @Override
                    public void run() {
                        app.elegirFondo();
                    }
                });
        opcion("Atenuar el fondo", Config.ATENUAR, 0, ATENUADOS);
        opcion("Tema", Config.TEMA, 0, TEMAS);
        opcion("Tamaño de los íconos", Config.TAMANO, 1, TAMANOS);
        opcion("Nombres de las apps", Config.NOMBRES, 0, NOMBRES);

        seccion("Reloj");
        opcion("Formato de hora", Config.HORA, 0, HORAS);
        opcion("Fecha", Config.FECHA, 0, SI_NO);

        seccion("Aplicaciones");
        fila("Elegir favoritas", "Marcar las apps del inicio", new Runnable() {
            @Override
            public void run() {
                app.elegirFavoritas();
            }
        });
        opcion("Botón \"Agregar\" en el inicio", Config.BOTON_AGREGAR, 0, SI_NO);
        int ocultas = config.ocultas().size();
        fila("Apps ocultas", ocultas == 0 ? "Ninguna" : "Seleccionadas: " + ocultas, new Runnable() {
            @Override
            public void run() {
                app.elegirOcultas();
            }
        });

        seccion("Sistema");
        fila("Launcher predeterminado",
                app.esPredeterminado() ? "Mi Launcher es el predeterminado" : "Tocar para configurar",
                new Runnable() {
                    @Override
                    public void run() {
                        app.elegirPredeterminado();
                    }
                });
        fila("Botón Inicio abre Mi Launcher",
                BotonInicioService.activado(app) ? "Activado" : "Desactivado: tocar para activar",
                new Runnable() {
                    @Override
                    public void run() {
                        app.configurarBotonInicio();
                    }
                });
        fila("Restablecer apariencia", "No borra tus favoritas", new Runnable() {
            @Override
            public void run() {
                app.mostrarDialogo(tema.dialogo(app)
                        .setTitle("¿Restablecer la apariencia?")
                        .setPositiveButton("Restablecer", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int i) {
                                config.restablecerApariencia();
                                app.reconstruir();
                            }
                        })
                        .setNegativeButton("Cancelar", null));
            }
        });
        fila("Cerrar ajustes", "Versión 1.2", new Runnable() {
            @Override
            public void run() {
                app.cerrarPanel();
            }
        });
        return desplazable;
    }

    private static int limitar(int valor, String[] valores) {
        return valor < 0 || valor >= valores.length ? 0 : valor;
    }

    /** Fila que pasa al siguiente valor cada vez que se pulsa. */
    private void opcion(String titulo, final String clave, final int inicial, final String[] valores) {
        final int actual = limitar(config.numero(clave, inicial), valores);
        fila(titulo, valores[actual], new Runnable() {
            @Override
            public void run() {
                config.poner(clave, (actual + 1) % valores.length);
                app.reconstruir();
            }
        });
    }

    private void seccion(String nombre) {
        TextView t = etiqueta(nombre, 14, tema.claro ? tema.seleccionado : Tema.ACENTO);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(app.px(16), app.px(16), 0, app.px(4));
        columna.addView(t);
    }

    private void fila(String titulo, String valor, final Runnable accion) {
        LinearLayout fila = new LinearLayout(app);
        fila.setOrientation(LinearLayout.VERTICAL);
        fila.setFocusable(true);
        fila.setClickable(true);
        fila.setPadding(app.px(16), app.px(10), app.px(16), app.px(10));

        StateListDrawable fondo = new StateListDrawable();
        GradientDrawable foco = new GradientDrawable();
        foco.setColor(tema.seleccionado);
        foco.setCornerRadius(app.px(14));
        fondo.addState(new int[]{android.R.attr.state_focused}, foco);
        fondo.addState(new int[]{}, new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        fila.setBackground(fondo);

        TextView arriba = etiqueta(titulo, 18, tema.texto);
        TextView abajo = etiqueta(valor, 14, tema.textoSecundario);
        arriba.setTextColor(tema.coloresBoton());
        arriba.setDuplicateParentStateEnabled(true);
        abajo.setTextColor(new android.content.res.ColorStateList(
                new int[][]{new int[]{android.R.attr.state_focused}, new int[]{}},
                new int[]{Color.WHITE, tema.textoSecundario}));
        abajo.setDuplicateParentStateEnabled(true);
        fila.addView(arriba);
        fila.addView(abajo);

        final int indice = filas.size();
        fila.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                app.recordarFila(indice);
                accion.run();
            }
        });
        filas.add(fila);
        columna.addView(fila, new LinearLayout.LayoutParams(-1, -2));
    }

    private TextView etiqueta(String texto, int tamano, int color) {
        TextView t = new TextView(app);
        t.setText(texto);
        t.setTextColor(color);
        app.texto(t, tamano);
        return t;
    }
}
