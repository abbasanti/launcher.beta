package com.santi.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pantalla de inicio: apps favoritas, todas las apps y accesos al sistema. */
public class MainActivity extends Activity {

    private static final int PEDIDO_ROL = 1;
    private static final int PEDIDO_IMAGEN = 2;
    private static final int PEDIDO_PERMISO = 3;
    private static final int[] TAMANOS = {104, 128, 152};
    private static final int SEPARACION = 24;
    private static final int ANCHO_UTIL = 1184;

    /** Favoritas iniciales (solo se muestran las que estén instaladas). */
    private static final String[] SUGERIDAS = {
            "com.google.android.youtube.tv",
            "com.netflix.ninja",
            "com.disney.disneyplus",
            "com.android.vending",
            "com.amazon.amazonvideo.livingroom",
            "com.wbd.stream"
    };

    private static final class App {
        String paquete;
        String nombre;
        String categoria;
        ComponentName componente;
        Drawable icono;
    }

    /** Proporción respecto del diseño original de 1280 x 720. */
    private float escala;
    private boolean verTodas;
    private boolean panelAbierto;
    private boolean detenido = true;
    private boolean irABarra;
    private int filaPanel;
    private long momentoStop;
    private String ultimaApp;
    private final Map<String, App> apps = new LinkedHashMap<String, App>();

    private Config config;
    private Tema tema;
    private AlertDialog dialogo;

    private FrameLayout raiz;
    private FrameLayout contenido;
    private FrameLayout barra;
    private View velo;
    private View panel;
    private View botonFavoritas;
    private View botonTodas;
    private View botonPanel;
    private View botonSistema;
    private WifiView wifi;

    private final Handler reloj = new Handler();
    private final Runnable revisarRed = new Runnable() {
        @Override
        public void run() {
            actualizarRed();
            reloj.postDelayed(this, 4000);
        }
    };

    // ---------------------------------------------------------------- Ciclo de vida

    @Override
    protected void onCreate(Bundle estado) {
        super.onCreate(estado);
        int ancho = getResources().getDisplayMetrics().widthPixels;
        int alto = getResources().getDisplayMetrics().heightPixels;
        escala = Math.min(Math.max(ancho, alto) / 1280f, Math.min(ancho, alto) / 720f);
        config = new Config(this);
        construirInterfaz(getResources().getConfiguration());
    }

    @Override
    protected void onResume() {
        super.onResume();
        cargarApps();
        if (panelAbierto) {
            // Actualiza los estados al volver de los ajustes del sistema.
            raiz.removeView(panel);
            agregarPanel();
        }
        mostrar(!panelAbierto && !irABarra);
        if (irABarra) {
            irABarra = false;
            enfocar(botonFavoritas);
        }
        reloj.removeCallbacks(revisarRed);
        revisarRed.run();
        preguntarPredeterminado();
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        detenido = false;
    }

    @Override
    protected void onPause() {
        reloj.removeCallbacks(revisarRed);
        super.onPause();
    }

    @Override
    protected void onStop() {
        detenido = true;
        momentoStop = SystemClock.elapsedRealtime();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        cerrarDialogo();
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Inicio pulsado estando en el launcher: el foco va a la barra superior.
        // Desde otra app, en cambio, se vuelve a las favoritas.
        // Si el servicio reemplazó al inicio original, el launcher acaba de ocultarse.
        boolean reciente = intent.getBooleanExtra(BotonInicioService.REDIRIGIDO, false)
                && SystemClock.elapsedRealtime() - momentoStop < 2000;
        irABarra = !detenido || reciente;
        cerrarDialogo();
        if (panelAbierto) {
            cerrarPanel();
        }
        verTodas = false;
        ultimaApp = null;
        // onResume() se ejecuta a continuación y redibuja la pantalla.
    }

    @Override
    public void onConfigurationChanged(Configuration nueva) {
        super.onConfigurationChanged(nueva);
        if (Tema.esClaro(config.numero(Config.TEMA, 0), nueva) != tema.claro) {
            cerrarDialogo();
            construirInterfaz(nueva);
            mostrar(!panelAbierto);
            actualizarRed();
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        // Un launcher no se cierra con "Atrás".
        if (panelAbierto) {
            cerrarPanel();
        } else if (verTodas) {
            cambiarVista(false, true);
        }
    }

    // ---------------------------------------------------------------- Interfaz

    private void construirInterfaz(Configuration configuracion) {
        tema = Tema.crear(Tema.esClaro(config.numero(Config.TEMA, 0), configuracion));

        raiz = new FrameLayout(this);
        raiz.setClipChildren(false);

        ImageView fondo = new ImageView(this);
        fondo.setImageDrawable(Fondos.crear(this, config.numero(Config.FONDO, 0), tema.claro));
        fondo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        raiz.addView(fondo, new FrameLayout.LayoutParams(-1, -1));

        int[] alfas = {0, 0x40, 0x80};
        int atenuar = Math.max(0, Math.min(2, config.numero(Config.ATENUAR, 0)));
        View sombreado = new View(this);
        sombreado.setBackgroundColor((alfas[atenuar] << 24) | (tema.claro ? 0xFFFFFF : 0));
        raiz.addView(sombreado, new FrameLayout.LayoutParams(-1, -1));

        velo = new View(this);
        velo.setBackgroundColor(tema.velo);
        raiz.addView(velo, new FrameLayout.LayoutParams(-1, -1));

        contenido = new FrameLayout(this);
        contenido.setClipChildren(false);
        raiz.addView(contenido, new FrameLayout.LayoutParams(-1, -1));

        crearBarraSuperior();
        setContentView(raiz);

        panel = null;
        if (panelAbierto) {
            agregarPanel();
        }
    }

    /** Vuelve a dibujar todo tras cambiar un ajuste. */
    void reconstruir() {
        construirInterfaz(getResources().getConfiguration());
        mostrar(!panelAbierto);
        actualizarRed();
    }

    private void crearBarraSuperior() {
        barra = new FrameLayout(this);
        LinearLayout izquierda = fila();

        LinearLayout hora = new LinearLayout(this);
        hora.setOrientation(LinearLayout.VERTICAL);
        boolean conFecha = config.numero(Config.FECHA, 0) == 0;
        String formato = config.numero(Config.HORA, 0) == 0 ? "HH:mm" : "h:mm a";
        TextClock horas = textoReloj(formato, conFecha ? 32 : 40);
        horas.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        hora.addView(horas);
        if (conFecha) {
            hora.addView(textoReloj("EEE d 'de' MMM", 15));
        }
        izquierda.addView(hora, margen(-2, -2, 0, 28));

        View tienda = boton("Abrir Play Store", R.drawable.ic_tienda);
        tienda.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                abrirTienda();
            }
        });
        izquierda.addView(tienda, margen(px(56), px(56), 0, 16));

        View usuarios = boton("Ver usuarios del dispositivo", R.drawable.ic_usuario);
        usuarios.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                abrirPrimero(new Intent("android.settings.USER_SETTINGS"),
                        new Intent(Settings.ACTION_SYNC_SETTINGS),
                        new Intent(Settings.ACTION_SETTINGS));
            }
        });
        izquierda.addView(usuarios, new LinearLayout.LayoutParams(px(56), px(56)));

        FrameLayout.LayoutParams posIzq = new FrameLayout.LayoutParams(-2, px(60), Gravity.LEFT);
        posIzq.setMargins(px(48), px(26), 0, 0);
        barra.addView(izquierda, posIzq);

        LinearLayout derecha = fila();

        LinearLayout selector = fila();
        selector.setBackground(forma(tema.boton, 0, 0));
        botonFavoritas = boton("Apps favoritas", Color.TRANSPARENT, R.drawable.ic_estrella);
        botonFavoritas.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cambiarVista(false, false);
            }
        });
        selector.addView(botonFavoritas, new LinearLayout.LayoutParams(px(92), px(56)));
        botonTodas = boton("Todas las apps", Color.TRANSPARENT, R.drawable.ic_grilla);
        botonTodas.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cambiarVista(true, false);
            }
        });
        selector.addView(botonTodas, new LinearLayout.LayoutParams(px(92), px(56)));
        derecha.addView(selector, margen(-2, px(56), 0, 16));

        botonPanel = boton("Ajustes del launcher", R.drawable.ic_launcher_ajustes);
        botonPanel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                abrirPanel();
            }
        });
        derecha.addView(botonPanel, margen(px(56), px(56), 0, 16));

        // Configuración y wifi forman un único botón.
        LinearLayout sistema = boton("Configuración y wifi", R.drawable.ic_ajustes);
        wifi = new WifiView(this, tema.coloresBoton());
        sistema.addView(wifi, margen(px(30), px(30), 20, 0));
        botonSistema = sistema;
        sistema.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                abrirPrimero(new Intent(Settings.ACTION_SETTINGS));
            }
        });
        derecha.addView(sistema, new LinearLayout.LayoutParams(px(138), px(56)));

        FrameLayout.LayoutParams posDer = new FrameLayout.LayoutParams(-2, px(56), Gravity.RIGHT);
        posDer.setMargins(0, px(28), px(48), 0);
        barra.addView(derecha, posDer);

        raiz.addView(barra, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
    }

    private TextClock textoReloj(String formato, int tamano) {
        TextClock t = new TextClock(this);
        t.setFormat12Hour(formato);
        t.setFormat24Hour(formato);
        t.setTextColor(tema.texto);
        t.setShadowLayer(px(6), 0, px(1), tema.sombra);
        t.setIncludeFontPadding(false);
        texto(t, tamano);
        return t;
    }

    private LinearLayout boton(String descripcion, int icono) {
        return boton(descripcion, tema.boton, icono);
    }

    private LinearLayout boton(String descripcion, int color, int icono) {
        LinearLayout b = fila();
        b.setGravity(Gravity.CENTER);
        b.setFocusable(true);
        b.setClickable(true);
        b.setContentDescription(descripcion);

        StateListDrawable fondo = new StateListDrawable();
        fondo.addState(new int[]{android.R.attr.state_focused}, forma(Tema.ACENTO, tema.bordeFoco, px(3)));
        fondo.addState(new int[]{android.R.attr.state_selected}, forma(tema.seleccionado, 0, 0));
        fondo.addState(new int[]{}, forma(color, 0, 0));
        b.setBackground(fondo);

        ImageView imagen = new ImageView(this);
        imagen.setImageResource(icono);
        imagen.setImageTintList(tema.coloresBoton());
        imagen.setDuplicateParentStateEnabled(true);
        b.addView(imagen, new LinearLayout.LayoutParams(px(30), px(30)));
        return b;
    }

    private GradientDrawable forma(int color, int borde, int grosor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(px(28));
        if (grosor > 0) {
            d.setStroke(grosor, borde);
        }
        return d;
    }

    private void actualizarRed() {
        if (wifi != null) {
            botonSistema.setContentDescription("Configuración y wifi, " + wifi.actualizar());
        }
    }

    // ---------------------------------------------------------------- Panel de ajustes

    private void abrirPanel() {
        panelAbierto = true;
        filaPanel = 0;
        agregarPanel();
    }

    private void agregarPanel() {
        // Mientras el panel está abierto, el foco no sale de él.
        contenido.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        barra.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        panel = PanelAjustes.crear(this, config, tema, filaPanel);
        raiz.addView(panel, new FrameLayout.LayoutParams(px(460), -1, Gravity.RIGHT));
    }

    void cerrarPanel() {
        panelAbierto = false;
        contenido.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        barra.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        if (panel != null) {
            raiz.removeView(panel);
            panel = null;
        }
        enfocar(botonPanel);
    }

    void recordarFila(int indice) {
        filaPanel = indice;
    }

    void mostrarDialogo(AlertDialog.Builder constructor) {
        cerrarDialogo();
        dialogo = constructor.show();
    }

    private void cerrarDialogo() {
        if (dialogo != null) {
            dialogo.dismiss();
            dialogo = null;
        }
    }

    void elegirFondo() {
        int actual = config.numero(Config.FONDO, 0);
        mostrarDialogo(tema.dialogo(this)
                .setTitle("Fondo de pantalla")
                .setSingleChoiceItems(Fondos.NOMBRES, actual, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int indice) {
                        d.dismiss();
                        if (indice == Fondos.PROPIO) {
                            elegirImagen();
                        } else {
                            config.poner(Config.FONDO, indice);
                            reconstruir();
                        }
                    }
                }));
    }

    private void elegirImagen() {
        Intent explorador = new Intent(Intent.ACTION_GET_CONTENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("image/*");
        if (disponible(explorador)) {
            try {
                startActivityForResult(explorador, PEDIDO_IMAGEN);
                return;
            } catch (RuntimeException e) {
                // Se usa la galería propia.
            }
        }
        abrirGaleria();
    }

    /** Lista de imágenes de la TV, para equipos sin explorador de archivos. */
    private void abrirGaleria() {
        String permiso = Build.VERSION.SDK_INT >= 33
                ? "android.permission.READ_MEDIA_IMAGES"
                : "android.permission.READ_EXTERNAL_STORAGE";
        if (checkSelfPermission(permiso) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{permiso}, PEDIDO_PERMISO);
            return;
        }
        final List<Uri> direcciones = new ArrayList<Uri>();
        List<String> nombres = new ArrayList<String>();
        Uri base = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        try {
            Cursor c = getContentResolver().query(base,
                    new String[]{MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME},
                    null, null, MediaStore.Images.Media.DATE_ADDED + " DESC");
            if (c != null) {
                while (c.moveToNext() && direcciones.size() < 200) {
                    direcciones.add(ContentUris.withAppendedId(base, c.getLong(0)));
                    nombres.add(String.valueOf(c.getString(1)));
                }
                c.close();
            }
        } catch (RuntimeException e) {
            direcciones.clear();
        }
        if (direcciones.isEmpty()) {
            mostrarDialogo(tema.dialogo(this)
                    .setTitle("No se encontraron imágenes")
                    .setMessage("Copiá una imagen a la memoria de la TV y volvé a intentar.")
                    .setPositiveButton("Aceptar", null));
            return;
        }
        mostrarDialogo(tema.dialogo(this)
                .setTitle("Elegir imagen")
                .setItems(nombres.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int indice) {
                        importarFondo(direcciones.get(indice));
                    }
                }));
    }

    private void importarFondo(final Uri origen) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean correcto = true;
                try {
                    Fondos.importar(getApplicationContext(), origen);
                } catch (Exception e) {
                    correcto = false;
                } catch (OutOfMemoryError e) {
                    correcto = false;
                }
                final boolean listo = correcto;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isDestroyed()) {
                            return;
                        }
                        if (listo) {
                            config.poner(Config.FONDO, Fondos.PROPIO);
                            reconstruir();
                        } else {
                            aviso("No se pudo usar esa imagen");
                        }
                    }
                });
            }
        }).start();
    }

    @Override
    public void onRequestPermissionsResult(int pedido, String[] permisos, int[] resultados) {
        if (pedido != PEDIDO_PERMISO) {
            return;
        }
        if (resultados.length > 0 && resultados[0] == PackageManager.PERMISSION_GRANTED) {
            abrirGaleria();
        } else {
            aviso("Sin permiso no se pueden ver las imágenes");
        }
    }

    // ---------------------------------------------------------------- Apps

    private void cargarApps() {
        PackageManager pm = getPackageManager();
        Map<String, App> encontradas = new LinkedHashMap<String, App>();
        // Primero las versiones para TV; luego las que solo tienen versión móvil.
        for (String categoria : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent consulta = new Intent(Intent.ACTION_MAIN).addCategory(categoria);
            for (ResolveInfo info : pm.queryIntentActivities(consulta, 0)) {
                String paquete = info.activityInfo.packageName;
                if (paquete.equals(getPackageName()) || encontradas.containsKey(paquete)) {
                    continue;
                }
                App app = new App();
                app.paquete = paquete;
                app.categoria = categoria;
                app.nombre = String.valueOf(info.loadLabel(pm));
                app.componente = new ComponentName(paquete, info.activityInfo.name);
                app.icono = info.loadIcon(pm);
                encontradas.put(paquete, app);
            }
        }
        List<App> ordenadas = new ArrayList<App>(encontradas.values());
        final Collator comparador = Collator.getInstance();
        Collections.sort(ordenadas, new Comparator<App>() {
            @Override
            public int compare(App a, App b) {
                return comparador.compare(a.nombre, b.nombre);
            }
        });
        apps.clear();
        for (App app : ordenadas) {
            apps.put(app.paquete, app);
        }
    }

    private List<String> favoritas() {
        List<String> lista = config.favoritas();
        if (lista == null) {
            lista = new ArrayList<String>();
            for (String paquete : SUGERIDAS) {
                if (apps.containsKey(paquete)) {
                    lista.add(paquete);
                }
            }
        }
        return lista;
    }

    void elegirFavoritas() {
        elegirVarias("Apps favoritas", favoritas(), true);
    }

    void elegirOcultas() {
        elegirVarias("Ocultar de Todas las apps", config.ocultas(), false);
    }

    /** Lista de todas las apps con casillas para marcar. */
    private void elegirVarias(String titulo, final List<String> actuales, final boolean sonFavoritas) {
        final List<App> lista = new ArrayList<App>(apps.values());
        String[] nombres = new String[lista.size()];
        final boolean[] marcadas = new boolean[lista.size()];
        for (int i = 0; i < lista.size(); i++) {
            nombres[i] = lista.get(i).nombre;
            marcadas[i] = actuales.contains(lista.get(i).paquete);
        }
        mostrarDialogo(tema.dialogo(this)
                .setTitle(titulo)
                .setMultiChoiceItems(nombres, marcadas, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int indice, boolean marcada) {
                        marcadas[indice] = marcada;
                    }
                })
                .setPositiveButton("Guardar", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int boton) {
                        // Se conserva el orden y las nuevas van al final.
                        for (int i = 0; i < lista.size(); i++) {
                            String paquete = lista.get(i).paquete;
                            if (!marcadas[i]) {
                                actuales.remove(paquete);
                            } else if (!actuales.contains(paquete)) {
                                actuales.add(paquete);
                            }
                        }
                        if (sonFavoritas) {
                            config.guardarFavoritas(actuales);
                        } else {
                            config.guardarOcultas(actuales);
                        }
                        reconstruir();
                    }
                })
                .setNegativeButton("Cancelar", null));
    }

    // ---------------------------------------------------------------- Contenido

    private void cambiarVista(boolean todas, boolean enfocarApp) {
        verTodas = todas;
        ultimaApp = null;
        mostrar(enfocarApp);
    }

    private void mostrar(boolean enfocarApp) {
        contenido.removeAllViews();
        velo.setVisibility(verTodas ? View.VISIBLE : View.GONE);
        botonFavoritas.setSelected(!verTodas);
        botonTodas.setSelected(verTodas);

        List<App> visibles = new ArrayList<App>();
        if (verTodas) {
            List<String> ocultas = config.ocultas();
            for (App app : apps.values()) {
                if (!ocultas.contains(app.paquete)) {
                    visibles.add(app);
                }
            }
        } else {
            for (String paquete : favoritas()) {
                if (apps.containsKey(paquete)) {
                    visibles.add(apps.get(paquete));
                }
            }
        }

        View destino = verTodas ? mostrarGrilla(visibles) : mostrarFavoritas(visibles);
        if (enfocarApp) {
            enfocar(destino == null ? botonTodas : destino);
        }
    }

    private void enfocar(final View vista) {
        vista.post(new Runnable() {
            @Override
            public void run() {
                vista.requestFocus();
            }
        });
    }

    private int tamanoIcono() {
        return TAMANOS[Math.max(0, Math.min(2, config.numero(Config.TAMANO, 1)))];
    }

    /** Fila inferior. Devuelve el elemento que debe recibir el foco. */
    private View mostrarFavoritas(List<App> lista) {
        boolean conAgregar = config.numero(Config.BOTON_AGREGAR, 0) == 0;
        if (lista.isEmpty()) {
            TextView ayuda = new TextView(this);
            ayuda.setText(conAgregar
                    ? "Todavía no hay favoritas. Usá el botón + para agregarlas."
                    : "Todavía no hay favoritas. Elegilas desde los ajustes del launcher.");
            ayuda.setTextColor(tema.texto);
            ayuda.setGravity(Gravity.CENTER);
            ayuda.setShadowLayer(px(6), 0, px(2), tema.sombra);
            texto(ayuda, 22);
            FrameLayout.LayoutParams pos = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
            pos.setMargins(px(48), 0, px(48), px(260));
            contenido.addView(ayuda, pos);
            if (!conAgregar) {
                return null;
            }
        }
        HorizontalScrollView desplazable = new HorizontalScrollView(this);
        desplazable.setHorizontalScrollBarEnabled(false);
        desplazable.setClipToPadding(false);
        desplazable.setFillViewport(true);

        LinearLayout fila = fila();
        fila.setGravity(Gravity.CENTER_HORIZONTAL);
        fila.setClipChildren(false);
        fila.setClipToPadding(false);
        fila.setPadding(px(16), px(16), px(16), 0);

        int tamano = tamanoIcono();
        View destino = null;
        int total = lista.size() + (conAgregar ? 1 : 0);
        for (int i = 0; i < total; i++) {
            View celda;
            if (i < lista.size()) {
                celda = celda(lista.get(i));
                if (destino == null || lista.get(i).paquete.equals(ultimaApp)) {
                    destino = celda.findViewWithTag(lista.get(i).paquete);
                }
            } else {
                celda = celdaAgregar();
                if (destino == null) {
                    destino = ((ViewGroup) celda).getChildAt(0);
                }
            }
            fila.addView(celda, margen(px(tamano), -2, 0, i == total - 1 ? 0 : SEPARACION));
        }
        desplazable.addView(fila);

        FrameLayout.LayoutParams pos = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        pos.setMargins(px(32), 0, px(32), px(34));
        contenido.addView(desplazable, pos);
        return destino;
    }

    private View mostrarGrilla(List<App> lista) {
        TextView titulo = new TextView(this);
        titulo.setText("Todas las apps");
        titulo.setTextColor(tema.texto);
        titulo.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        texto(titulo, 26);
        FrameLayout.LayoutParams posTitulo = new FrameLayout.LayoutParams(-2, -2);
        posTitulo.setMargins(px(48), px(104), 0, 0);
        contenido.addView(titulo, posTitulo);

        ScrollView desplazable = new ScrollView(this);
        desplazable.setVerticalScrollBarEnabled(false);

        LinearLayout columna = new LinearLayout(this);
        columna.setOrientation(LinearLayout.VERTICAL);
        columna.setClipChildren(false);
        columna.setPadding(px(16), px(16), px(16), px(24));

        int tamano = tamanoIcono();
        int columnas = (ANCHO_UTIL + SEPARACION) / (tamano + SEPARACION);
        View destino = null;
        LinearLayout fila = null;
        for (int i = 0; i < lista.size(); i++) {
            if (i % columnas == 0) {
                fila = fila();
                fila.setClipChildren(false);
                columna.addView(fila, new LinearLayout.LayoutParams(-2, -2));
            }
            View celda = celda(lista.get(i));
            fila.addView(celda, margen(px(tamano), -2, 0, i % columnas == columnas - 1 ? 0 : SEPARACION));
            if (destino == null || lista.get(i).paquete.equals(ultimaApp)) {
                destino = celda.findViewWithTag(lista.get(i).paquete);
            }
        }
        desplazable.addView(columna, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL));

        FrameLayout.LayoutParams pos = new FrameLayout.LayoutParams(-1, -1);
        pos.setMargins(px(32), px(146), px(32), 0);
        contenido.addView(desplazable, pos);
        return destino;
    }

    private View celda(final App app) {
        TileView icono = new TileView(this, app.icono, escala, tema.tarjeta, 0.72f);
        icono.setTag(app.paquete);
        icono.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent lanzar = new Intent(Intent.ACTION_MAIN)
                        .addCategory(app.categoria)
                        .setComponent(app.componente)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                abrirPrimero(lanzar);
            }
        });
        icono.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                mostrarOpciones(app);
                return true;
            }
        });
        return conNombre(icono, app.nombre, app.paquete);
    }

    /** Botón dedicado para sumar apps a las favoritas. */
    @SuppressWarnings("deprecation")
    private View celdaAgregar() {
        Drawable mas = getResources().getDrawable(R.drawable.ic_mas).mutate();
        mas.setTint(tema.texto);
        TileView icono = new TileView(this, mas, escala, tema.boton, 0.45f);
        icono.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                elegirFavoritas();
            }
        });
        return conNombre(icono, "Agregar apps", null);
    }

    /** Ícono cuadrado con el nombre debajo. */
    private View conNombre(TileView icono, String etiqueta, final String paquete) {
        LinearLayout celda = new LinearLayout(this);
        celda.setOrientation(LinearLayout.VERTICAL);
        celda.setClipChildren(false);

        final boolean siempre = config.numero(Config.NOMBRES, 0) == 1;
        final TextView nombre = new TextView(this);
        nombre.setText(etiqueta);
        nombre.setTextColor(tema.texto);
        nombre.setGravity(Gravity.CENTER);
        nombre.setSingleLine(true);
        nombre.setEllipsize(TextUtils.TruncateAt.END);
        nombre.setShadowLayer(px(6), 0, px(1), tema.sombra);
        texto(nombre, 15);
        nombre.setVisibility(siempre ? View.VISIBLE : View.INVISIBLE);

        icono.setContentDescription(etiqueta);
        icono.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean conFoco) {
                nombre.setVisibility(conFoco || siempre ? View.VISIBLE : View.INVISIBLE);
                float zoom = conFoco ? 1.08f : 1f;
                v.animate().scaleX(zoom).scaleY(zoom).setDuration(150).start();
                if (conFoco && paquete != null) {
                    ultimaApp = paquete;
                }
                v.invalidate();
            }
        });

        int tamano = tamanoIcono();
        celda.addView(icono, new LinearLayout.LayoutParams(px(tamano), px(tamano)));
        LinearLayout.LayoutParams posNombre = new LinearLayout.LayoutParams(px(tamano), px(28));
        posNombre.topMargin = px(8);
        celda.addView(nombre, posNombre);
        return celda;
    }

    private void mostrarOpciones(final App app) {
        final List<String> lista = favoritas();
        final List<String> opciones = new ArrayList<String>();
        if (!lista.contains(app.paquete)) {
            opciones.add("Agregar a favoritas");
        } else {
            if (!verTodas) {
                opciones.add("Mover a la izquierda");
                opciones.add("Mover a la derecha");
            }
            opciones.add("Quitar de favoritas");
        }
        if (verTodas) {
            opciones.add("Ocultar de esta lista");
        }
        opciones.add("Información de la app");

        mostrarDialogo(tema.dialogo(this)
                .setTitle(app.nombre)
                .setItems(opciones.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int indice) {
                        String opcion = opciones.get(indice);
                        if (opcion.startsWith("Información")) {
                            abrirPrimero(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", app.paquete, null)));
                            return;
                        }
                        if (opcion.startsWith("Ocultar")) {
                            List<String> ocultas = config.ocultas();
                            ocultas.add(app.paquete);
                            config.guardarOcultas(ocultas);
                            ultimaApp = null;
                        } else {
                            if (opcion.startsWith("Agregar")) {
                                lista.add(app.paquete);
                            } else if (opcion.startsWith("Quitar")) {
                                lista.remove(app.paquete);
                            } else {
                                mover(lista, app.paquete, opcion.endsWith("izquierda") ? -1 : 1);
                            }
                            config.guardarFavoritas(lista);
                            ultimaApp = app.paquete;
                        }
                        mostrar(true);
                    }
                }));
    }

    /** Intercambia con la favorita vecina que esté instalada. */
    private void mover(List<String> lista, String paquete, int paso) {
        int origen = lista.indexOf(paquete);
        for (int i = origen + paso; i >= 0 && i < lista.size(); i += paso) {
            if (apps.containsKey(lista.get(i))) {
                Collections.swap(lista, origen, i);
                return;
            }
        }
    }

    // ---------------------------------------------------------------- Launcher predeterminado

    boolean esPredeterminado() {
        Intent inicio = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo info = getPackageManager().resolveActivity(inicio, PackageManager.MATCH_DEFAULT_ONLY);
        return info != null && info.activityInfo != null
                && getPackageName().equals(info.activityInfo.packageName);
    }

    /** Solo la primera vez que se abre el launcher. */
    private void preguntarPredeterminado() {
        if (config.numero(Config.PREGUNTADO, 0) == 1) {
            return;
        }
        config.poner(Config.PREGUNTADO, 1);
        if (esPredeterminado()) {
            return;
        }
        mostrarDialogo(tema.dialogo(this)
                .setTitle("¿Usar Mi Launcher como predeterminado?")
                .setMessage("Se abriría al encender la TV y al pulsar Inicio. "
                        + "También podés hacerlo después desde los ajustes del launcher.")
                .setPositiveButton("Elegir", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int boton) {
                        elegirPredeterminado();
                    }
                })
                .setNegativeButton("Ahora no", null));
    }

    void elegirPredeterminado() {
        if (esPredeterminado()) {
            if (!abrirAjustesInicio()) {
                aviso("Mi Launcher ya es el predeterminado");
            }
            return;
        }
        // Android 10 o superior: diálogo del sistema para elegir la app de inicio.
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                Object roles = getSystemService("role");
                String inicio = "android.app.role.HOME";
                boolean existe = (Boolean) roles.getClass()
                        .getMethod("isRoleAvailable", String.class).invoke(roles, inicio);
                if (existe) {
                    Intent pedido = (Intent) roles.getClass()
                            .getMethod("createRequestRoleIntent", String.class).invoke(roles, inicio);
                    startActivityForResult(pedido, PEDIDO_ROL);
                    return;
                }
            } catch (Exception e) {
                // Se intenta con los ajustes del sistema.
            }
        }
        if (!abrirAjustesInicio()) {
            explicarPredeterminado();
        }
    }

    private boolean abrirAjustesInicio() {
        Intent ajustes = new Intent("android.settings.HOME_SETTINGS");
        if (!disponible(ajustes)) {
            return false;
        }
        try {
            startActivity(ajustes);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void explicarPredeterminado() {
        mostrarDialogo(tema.dialogo(this)
                .setTitle("No se cambió el launcher")
                .setMessage("Algunos equipos, sobre todo con Google TV, no permiten elegir otro "
                        + "launcher. Podés hacer que el botón Inicio abra Mi Launcher.")
                .setPositiveButton("Configurar botón Inicio", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int boton) {
                        configurarBotonInicio();
                    }
                })
                .setNegativeButton("Cerrar", null));
    }

    void configurarBotonInicio() {
        boolean activo = BotonInicioService.activado(this);
        mostrarDialogo(tema.dialogo(this)
                .setTitle(activo ? "Botón Inicio activado" : "Activar el botón Inicio")
                .setMessage(activo
                        ? "Para desactivarlo, entrá en Accesibilidad y apagá \"Mi Launcher: botón Inicio\"."
                        : "En la pantalla de Accesibilidad, activá \"Mi Launcher: botón Inicio\". "
                                + "El servicio no lee el contenido de la pantalla.")
                .setPositiveButton("Abrir Accesibilidad", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int boton) {
                        abrirPrimero(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                                new Intent(Settings.ACTION_SETTINGS));
                    }
                })
                .setNegativeButton("Cerrar", null));
    }

    @Override
    protected void onActivityResult(int pedido, int resultado, Intent datos) {
        if (pedido == PEDIDO_ROL) {
            if (esPredeterminado()) {
                reconstruir();
            } else {
                explicarPredeterminado();
            }
        } else if (pedido == PEDIDO_IMAGEN && resultado == RESULT_OK
                && datos != null && datos.getData() != null) {
            importarFondo(datos.getData());
        }
    }

    // ---------------------------------------------------------------- Sistema

    /** Hay una app real que atienda el pedido (no un reemplazo vacío del sistema). */
    private boolean disponible(Intent intento) {
        ResolveInfo info = getPackageManager().resolveActivity(intento, 0);
        return info != null && info.activityInfo != null
                && !info.activityInfo.packageName.contains("stubs");
    }

    private void abrirTienda() {
        PackageManager pm = getPackageManager();
        abrirPrimero(pm.getLeanbackLaunchIntentForPackage("com.android.vending"),
                pm.getLaunchIntentForPackage("com.android.vending"),
                new Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=tv")));
    }

    /** Prueba cada intent en orden hasta que uno funcione. */
    private void abrirPrimero(Intent... intentos) {
        for (Intent intento : intentos) {
            if (intento == null) {
                continue;
            }
            try {
                startActivity(intento);
                return;
            } catch (RuntimeException e) {
                // No disponible en este equipo: se prueba el siguiente.
            }
        }
        aviso("No disponible en este dispositivo");
    }

    private void aviso(String mensaje) {
        Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show();
    }

    // ---------------------------------------------------------------- Utilidades

    int px(int valor) {
        return Math.round(valor * escala);
    }

    void texto(TextView vista, int tamano) {
        vista.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(tamano));
    }

    private LinearLayout fila() {
        LinearLayout fila = new LinearLayout(this);
        fila.setOrientation(LinearLayout.HORIZONTAL);
        fila.setGravity(Gravity.CENTER_VERTICAL);
        return fila;
    }

    /** Medidas en píxeles; márgenes laterales en unidades del diseño. */
    private LinearLayout.LayoutParams margen(int ancho, int alto, int izquierda, int derecha) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ancho, alto);
        p.leftMargin = px(izquierda);
        p.rightMargin = px(derecha);
        return p;
    }
}
