package ar.com.lichtmaier.antenas;

import android.annotation.TargetApi;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatDialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.util.Random;

public class Pagame extends AppCompatDialogFragment
{
	private static final String FRAGMENT_TAG = "pagame";

	private Guiño guiño;

	static void mostrar(FragmentActivity activity)
	{
		FragmentTransaction ft = activity.getSupportFragmentManager().beginTransaction();
		ft.addToBackStack(null);
		new Pagame().show(ft, FRAGMENT_TAG);
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState)
	{
		View v = inflater.inflate(R.layout.pagame, container, false);
		v.findViewById(R.id.boton_comprar).setOnClickListener(view -> {
			if(!isStateSaved())
				dismiss();
			((AntenaActivity)getActivity()).pagar();
		});
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
		{
			AnimatedVectorDrawable ojito = (AnimatedVectorDrawable)getContext().getDrawable(R.drawable.ic_emoji_u1f603_ojito);
			((ImageView)v.findViewById(R.id.pagame_carita)).setImageDrawable(ojito);
			guiño = new Guiño(ojito);
		}
		return v;
	}

	@Override
	public int getTheme()
	{
		return R.style.Pagame;
	}

	@Override
	public void onStart()
	{
		super.onStart();
		if(guiño != null)
			guiño.guiñar();
	}

	@Override
	public void onStop()
	{
		if(guiño != null)
			guiño.removeMessages(0);
		super.onStop();
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private static class Guiño extends Handler
	{
		private final AnimatedVectorDrawable ojito;
		private final Random random = new Random();

		Guiño(AnimatedVectorDrawable ojito)
		{
			this.ojito = ojito;
		}

		@Override
		public void handleMessage(Message msg)
		{
			ojito.start();
			guiñar();
		}

		void guiñar()
		{
			sendEmptyMessageDelayed(0, 2000 + random.nextInt(8192));
		}
	}
}
