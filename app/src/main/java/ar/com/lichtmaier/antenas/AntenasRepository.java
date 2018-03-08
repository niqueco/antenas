package ar.com.lichtmaier.antenas;

class AntenasRepository
{
	static class AntenaListada
	{
		final Antena antena;
		double distancia;
		final boolean lejos;

		AntenaListada(Antena antena, double distancia, boolean lejos)
		{
			this.antena = antena;
			this.distancia = distancia;
			this.lejos = lejos;
		}

		@Override
		public String toString()
		{
			StringBuilder sb = new StringBuilder();
			sb.append('{').append(antena).append(' ').append((int)distancia).append('m');
			if(lejos)
				sb.append(" lejos");
			sb.append('}');
			return sb.toString();
		}
	}
}
