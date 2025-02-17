package org.magic.gui.components.charts;

import java.util.Map.Entry;

import org.jfree.chart3d.data.PieDataset3D;
import org.jfree.chart3d.data.StandardPieDataset3D;
import org.magic.api.beans.MagicCard;
import org.magic.gui.abstracts.charts.Abstract3DPieChart;

public class TypeRepartitionPanel extends Abstract3DPieChart<MagicCard,String> {

	public TypeRepartitionPanel(boolean displayPanel) {
		super(displayPanel);
	}


	private static final long serialVersionUID = 1L;
	
	public PieDataset3D<String> getDataSet() {
		var dataset = new StandardPieDataset3D<String>();
		for (Entry<String, Integer> entry : manager.analyseTypes(items).entrySet()) {
			dataset.add(entry.getKey(), entry.getValue());
		}
		return dataset;
	}


	@Override
	public String getTitle() {
		return "Type";
	}

}
