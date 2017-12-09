package ar.com.lichtmaier.antenas;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatDialogFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crash.FirebaseCrash;

public class Calificame extends AppCompatDialogFragment implements View.OnClickListener
{
	private static final String PREF_CONTADOR_LANZAMIENTOS = "calif_contador_lanzamientos";
	private static final String PREF_PRIMER_LANZAMIENTO = "calif_primer_lanzamiento";
	private static final String PREF_CUANDO_PREGUNTAR = "calif_cuando_preguntar";
	private static final String PREF_MIRO_ANTENAS = "miro-antenas";
	private static final String PREF_MIRO_MAPA = "miro-mapa";

	private static final int LANZAMIENTOS = 3;
	private static final int DÍAS = 2;

	private static final String FRAGMENT_TAG = "calificame";

	private static boolean nuncaPreguntar = false;

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState)
	{
		View v = inflater.inflate(R.layout.calificame, container, false);

		v.findViewById(R.id.botón_después_califico).setOnClickListener(this);
		v.findViewById(R.id.botón_sí_califico).setOnClickListener(this);
		v.findViewById(R.id.botón_nunca_califico).setOnClickListener(this);

		return v;
	}

	@Override
	public int getTheme()
	{
		return R.style.Dialogo;
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState)
	{
		Dialog dialog = super.onCreateDialog(savedInstanceState);
		dialog.setCanceledOnTouchOutside(false);
		dialog.setTitle(R.string.calif_titulo);
		return dialog;
	}

	public static void registráQueMiróLasAntenas(Context context)
	{
		registrá(context, PREF_MIRO_ANTENAS);
	}

	public static void registráQueMiróElMapa(Context context)
	{
		registrá(context, PREF_MIRO_MAPA);
	}

	private static void registrá(Context context, String pref)
	{
		if(nuncaPreguntar)
			return;
		PreferenceManager.getDefaultSharedPreferences(context).edit()
				.putBoolean(pref, true).apply();
	}

	public static void registrarLanzamiento(FragmentActivity activity)
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);

		try
		{
			final long ahora = System.currentTimeMillis();

			long cuandoPreguntar = prefs.getLong(PREF_CUANDO_PREGUNTAR, ahora);

			if(cuandoPreguntar == -1)
			{
				nuncaPreguntar = true;
				return;
			}

			SharedPreferences.Editor editor = prefs.edit();

			long primerLanzamiento = prefs.getLong(PREF_PRIMER_LANZAMIENTO, -1);
			if(primerLanzamiento == -1)
			{
				primerLanzamiento = ahora;
				editor.putLong(PREF_PRIMER_LANZAMIENTO, primerLanzamiento);
			}

			int contadorLanzamientos = prefs.getInt(PREF_CONTADOR_LANZAMIENTOS, 0) + 1;
			editor.putInt(PREF_CONTADOR_LANZAMIENTOS, contadorLanzamientos);

			editor.apply();

			if(contadorLanzamientos > LANZAMIENTOS && cuandoPreguntar <= ahora && prefs.getBoolean(PREF_MIRO_ANTENAS, false) && prefs.getBoolean(PREF_MIRO_MAPA, false))
			{
				FragmentManager fm = activity.getSupportFragmentManager();
				if(fm.findFragmentByTag(FRAGMENT_TAG) != null)
					return;
				FragmentTransaction tr = fm.beginTransaction();
				tr.addToBackStack(null);
				new Calificame().show(tr, FRAGMENT_TAG);
			}

		} catch(Exception e)
		{
			Log.e("antenas", "Al pedir calificación...", e);
			FirebaseCrash.report(e);
		}
	}

	static boolean mostrando(FragmentActivity activity)
	{
		FragmentManager fm = activity.getSupportFragmentManager();
		return fm != null && fm.findFragmentByTag(FRAGMENT_TAG) != null;
	}

	@Override
	public void onClick(View v)
	{
		if(!isStateSaved())
			dismiss();
		acción(v.getId(), getActivity());
	}

	@SuppressLint({"CommitPrefEdits", "MissingPermission"})
	private static void acción(final int id, Activity context)
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		SharedPreferences.Editor editor = prefs.edit();
		String acción;
		switch(id)
		{
			case R.id.botón_sí_califico:
				Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=ar.com.lichtmaier.antenas"));
				if(intent.resolveActivity(context.getPackageManager()) != null)
					context.startActivity(intent);
				else
					Toast.makeText(context, R.string.app_no_disponible, Toast.LENGTH_LONG).show();
				editor.putLong(PREF_CUANDO_PREGUNTAR, -1);
				editor.remove(PREF_MIRO_MAPA);
				editor.remove(PREF_MIRO_ANTENAS);
				editor.remove(PREF_CONTADOR_LANZAMIENTOS);
				acción = "Califico";
				break;

			case R.id.botón_después_califico:
				editor.putLong(PREF_CUANDO_PREGUNTAR, System.currentTimeMillis() + DÍAS * 24 * 60 * 60 * 1000);
				acción = "Califico después";
				break;

			case R.id.botón_nunca_califico:
				editor.putLong(PREF_CUANDO_PREGUNTAR, -1);
				editor.remove(PREF_MIRO_MAPA);
				editor.remove(PREF_MIRO_ANTENAS);
				editor.remove(PREF_CONTADOR_LANZAMIENTOS);
				acción = "Califico nunca";
				break;

			default:
				throw new RuntimeException("id = " + id);
		}
		Bundle bundle = new Bundle();
		bundle.putString("accion", acción);
		FirebaseAnalytics.getInstance(context).logEvent("califica", bundle);
		editor.apply();
	}
}
