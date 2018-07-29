package org.magic.game.model.abilities.costs;

public class LoyaltyCost extends NumberCost{

	public LoyaltyCost(int value) {
		super(value);
	}
	
	public LoyaltyCost(String string) {
		super(string);
	}

	@Override
	public String toString()
	{
		if(getValue()==null)
			return getModifier()+"X";
		return (getValue()>0) ? "+"+getValue(): String.valueOf(getValue());
	}
	
	
}
