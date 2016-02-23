package ar.com.lichtmaier.antenas;

import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatDialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class BienvenidaFragment extends AppCompatDialogFragment implements View.OnClickListener
{
	public static void mostrar(FragmentActivity activity)
	{
		FragmentTransaction tr = activity.getSupportFragmentManager().beginTransaction();
		tr.addToBackStack(null);
		new BienvenidaFragment().show(tr, "bienvenida");
	}

	@Override
	public int getTheme()
	{
		return R.style.Dialogo;
	}

	@Override
	public void setupDialog(Dialog dialog, int style)
	{
		super.setupDialog(dialog, style);
		dialog.setTitle("Compass calibration");
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState)
	{
		View v = inflater.inflate(R.layout.bienvenida, container, false);
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
