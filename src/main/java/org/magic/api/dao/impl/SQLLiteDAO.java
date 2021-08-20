package org.magic.api.dao.impl;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.magic.api.interfaces.abstracts.AbstractMagicSQLDAO;
import org.magic.services.MTGConstants;
import org.magic.tools.FileTools;

public class SQLLiteDAO extends AbstractMagicSQLDAO {

	@Override
	public long getDBSize() {
		return FileUtils.sizeOf(getFile(SERVERNAME));
	}

	@Override
	public void backup(File dir) throws IOException {
		FileTools.zip(getFile(SERVERNAME), new File(dir, "backup.zip"));
	}
	
	
	
	@Override
	public Map<String, String> getDefaultAttributes() {
		return Map.of(SERVERNAME, Paths.get(MTGConstants.DATA_DIR.getAbsolutePath(),"sqlite-db").toFile().getAbsolutePath(),
				   LOGIN, "SA",
				   PASS, "",
				   DB_NAME, ""
				);
	}
	
	@Override
	protected String getdbSizeQuery() {
		return null;
	}

	@Override
	public String getName() {
		return "SQLite";
	}

	@Override
	protected String getAutoIncrementKeyWord() {
		return "INTEGER";// primary key column is autoincrement
	}

	@Override
	protected String getjdbcnamedb() {
		return getName().toLowerCase();
	}

	@Override
	protected String beanStorage() {
		return "json";
	}
	
	
	@Override
	protected String longTextStorage() {
		return "TEXT";
	}
	
	@Override
	protected String createListStockSQL() {
		return "select * from stocks where collection=? and JSON_EXTRACT(mcard,'$.name')=?";

	}
	
}
