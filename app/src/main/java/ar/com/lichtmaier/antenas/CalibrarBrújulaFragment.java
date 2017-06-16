package ar.com.lichtmaier.antenas;

import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatDialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class CalibrarBrújulaFragment extends AppCompatDialogFragment implements View.OnClickListener
{
	private static final String FRAGMENT_TAG = "calibrar";

	private static boolean seMostró = false;

	public static void mostrar(FragmentActivity activity)
	{
		if(seMostró || !activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_SENSOR_COMPASS) || activity.getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG) != null)
			return;

		FragmentTransaction tr = activity.getSupportFragmentManager().beginTransaction();
		tr.addToBackStack(null);
		new CalibrarBrújulaFragment().show(tr, FRAGMENT_TAG);
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
		dialog.setTitle(R.string.título_ayuda_calibración_brújula);
		return dialog;
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState)
	{
		seMostró = true;
		View v = inflater.inflate(R.layout.calibrar, container, false);
		v.findViewById(R.id.botón_ayuda_calibración).setOnClickListener(this);
		return v;
	}

	@Override
	public void onClick(View v)
	{
		Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://support.google.com/gmm/answer/6145351"));
		getActivity().startActivity(i);
		dismiss();
	}
}
