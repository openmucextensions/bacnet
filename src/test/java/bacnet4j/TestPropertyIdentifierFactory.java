package bacnet4j;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

import org.junit.Before;
import org.junit.Test;
import org.openmucextensions.driver.bacnet.PropertyIdentifierFactory;

import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;

public class TestPropertyIdentifierFactory {

	@Before
	public void setUp() throws Exception {
	}

	@Test
	public void test() {
		
		final String identifierString = "maxPresValue";
		
		PropertyIdentifier identifier = PropertyIdentifierFactory.getPropertyIdentifier(identifierString);
		assertThat(identifier.toString(), is(identifierString));
	}

}
