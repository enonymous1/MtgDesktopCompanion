package unit.apps;

import static org.junit.Assert.fail;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;
import org.magic.services.ModuleInstaller;

public class ModulesTest {

	ModuleInstaller installer;
	
	@Before
	public void init()
	{
		installer = new ModuleInstaller();
	}
	
	
	
	@Test
	public void lookupModules()
	{
		try {
			System.out.println("Missings " + installer.extractMissing("org.magic.api.decksniffer.impl", "/decksniffer/sniffer"));
			System.out.println(installer.updateConfigWithNewModule());
		} catch (Exception e) {
				fail(e.getMessage());
		} 
	}
}
