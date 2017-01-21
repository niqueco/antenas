package ar.com.lichtmaier.antenas;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatDialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class Pagame extends AppCompatDialogFragment
{
	private static final String FRAGMENT_TAG = "pagame";

	static void mostrar(FragmentActivity activity)
	{
		FragmentTransaction ft = activity.getSupportFragmentManager().beginTransaction();
		ft.addToBackStack(null);
		new Pagame().show(ft, FRAGMENT_TAG);
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState)
	{
		View v = inflater.inflate(R.layout.pagame, container, false);
		v.findViewById(R.id.boton_comprar).setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View view)
			{
				((AntenaActivity)getActivity()).pagar();
				dismiss();
			}
		});
		return v;
	}

	@Override
	public int getTheme()
	{
		return R.style.Pagame;
	}
}
