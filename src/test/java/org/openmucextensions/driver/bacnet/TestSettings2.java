package org.openmucextensions.driver.bacnet;


public class TestSettings2 {
// TODO: remove this class and adapt TestSettings and documentation accordingly
	
	
//	private final static String SETTING_SCAN_DISCOVERYSLEEPTIME = "discoverySleepTime";
//	private final static String SETTING_SCAN_PORT = "port";
//
//	private static final long defaultDiscoverySleepTime = 2*1000;
//
//	private ArgumentParser prepareParser()
//	{
//        ArgumentParser parser = ArgumentParsers.newArgumentParser("bacnetScanSettings", false)
//                .description("Settings for BACnet device discovery");
//        parser.addArgument("-" + SETTING_SCAN_DISCOVERYSLEEPTIME)
//        		.required(false)
//        		.type(Long.class)
//        		.setDefault(defaultDiscoverySleepTime)
//        		.help("Wait time during discovery of single port in ms");
//        parser.addArgument("-" + SETTING_SCAN_PORT)
//				.required(false)
//				.type(Long.class)
//				.setDefault(defaultDiscoverySleepTime)
//				.help("The port number used for connect");
//        return parser;
//	}
//	
//	private Namespace parse(String settings) throws ArgumentParserException
//	{
//		ArgumentParser parser = prepareParser();
//        final Long discSleep;
//        final Namespace res = parser.parseArgs((settings == null) ? new String[0] : settings.split("\\s+", 0));
//        return res;
//	}
//	
//	@Test
//	public void test1() throws ArgumentParserException {
//		final Namespace res = parse("-discoverySleepTime 300");
//		final Long dst = res.getLong(SETTING_SCAN_DISCOVERYSLEEPTIME);
//		Assert.assertEquals(Long.valueOf(300), dst);
//	}
//
//	@Test
//	public void test2() throws ArgumentParserException {
//		final Namespace res = parse(null);
//		final Long dst = res.getLong(SETTING_SCAN_DISCOVERYSLEEPTIME);
//		Assert.assertEquals(Long.valueOf(2000), dst);
//	}
}
