package edu.kit.ipd.parse.wsd;

import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

	private static final Logger logger = LoggerFactory.getLogger(WsdTest.class);

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
		logger.debug("Runnning SNLP");
		snlp.exec(ppd);
		logger.debug("Runnning graph builder");
		gb.exec(ppd);
		wsd.setGraph(ppd.getGraph());
		logger.debug("Runnning WSD");
		wsd.exec();
		System.out.println(wsd.getGraph().showGraph());
	}

}
