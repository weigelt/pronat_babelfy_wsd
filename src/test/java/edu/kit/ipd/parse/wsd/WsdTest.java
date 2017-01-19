package edu.kit.ipd.parse.wsd;

import org.junit.BeforeClass;
import org.junit.Test;

import edu.kit.ipd.parse.graphBuilder.GraphBuilder;
import edu.kit.ipd.parse.luna.data.MissingDataException;
import edu.kit.ipd.parse.luna.data.PrePipelineData;
import edu.kit.ipd.parse.luna.pipeline.PipelineStageException;
import edu.kit.ipd.parse.luna.tools.StringToHypothesis;
import edu.kit.ipd.parse.shallownlp.ShallowNLP;

public class WsdTest {

	private static Wsd wsd;
	private static GraphBuilder gb;
	private static ShallowNLP snlp;

	PrePipelineData ppd;

	@BeforeClass
	public static void setUp() {
		wsd = new Wsd();
		wsd.init();
		gb = new GraphBuilder();
		gb.init();
		snlp = new ShallowNLP();
		snlp.init();

	}

	@Test
	public void StringTest() throws PipelineStageException, MissingDataException {
		ppd = new PrePipelineData();
		String input = "Armar please prepare me a meal there are plates in the dishwasher please get a plate from the dishwasher and clean it afterwards put the instant meal from the fridge on it and warm it in the microwave for two minutes when it is done get the plate out of the microwave and put it on the kitchen table then you are done";
		ppd.setMainHypothesis(StringToHypothesis.stringToMainHypothesis(input));
		snlp.exec(ppd);
		gb.exec(ppd);
		wsd.setGraph(ppd.getGraph());
		wsd.exec();

	}

}
