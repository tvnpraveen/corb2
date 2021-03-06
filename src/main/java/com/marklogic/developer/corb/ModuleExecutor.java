/*
 * Copyright (c) 2004-2016 MarkLogic Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * The use of the Apache License does not indicate that this project is
 * affiliated with the Apache Software Foundation.
 */
package com.marklogic.developer.corb;

import static com.marklogic.developer.corb.Options.EXPORT_FILE_DIR;
import static com.marklogic.developer.corb.Options.EXPORT_FILE_NAME;
import static com.marklogic.developer.corb.Options.MODULES_DATABASE;
import static com.marklogic.developer.corb.Options.MODULE_ROOT;
import static com.marklogic.developer.corb.Options.OPTIONS_FILE;
import static com.marklogic.developer.corb.Options.PROCESS_MODULE;
import static com.marklogic.developer.corb.Options.XCC_CONNECTION_URI;
import static com.marklogic.developer.corb.util.IOUtils.closeQuietly;
import com.marklogic.developer.corb.util.StringUtils;
import static com.marklogic.developer.corb.util.StringUtils.isBlank;
import static com.marklogic.developer.corb.util.StringUtils.isJavaScriptModule;
import static com.marklogic.developer.corb.util.StringUtils.buildModulePath;
import static com.marklogic.developer.corb.util.StringUtils.isInlineModule;
import static com.marklogic.developer.corb.util.StringUtils.isInlineOrAdhoc;
import static com.marklogic.developer.corb.util.StringUtils.trim;
import com.marklogic.xcc.AdhocQuery;
import com.marklogic.xcc.Request;
import com.marklogic.xcc.RequestOptions;
import com.marklogic.xcc.ResultItem;
import com.marklogic.xcc.ResultSequence;
import com.marklogic.xcc.Session;
import com.marklogic.xcc.exceptions.RequestException;
import com.marklogic.xcc.exceptions.XccConfigException;
import com.marklogic.xcc.types.XdmBinary;
import com.marklogic.xcc.types.XdmItem;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * This class replaces RunXQuery. It can run both XQuery and JavaScript and when
 * built, doesn't wrap the XCC connection jar as RunXQuery does.
 *
 * @author matthew.heckel MarkLogic Corportation
 *
 */
public class ModuleExecutor extends AbstractManager {

    protected static final String NAME = ModuleExecutor.class.getSimpleName();

    private Session session;
    private ResultSequence res;

    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
    protected static final byte[] NEWLINE
            = System.getProperty("line.separator") != null ? System.getProperty("line.separator").getBytes() : "\n".getBytes();

    protected static final Logger LOG = Logger.getLogger(ModuleExecutor.class.getName());

    /**
     * @param args
     */
    public static void main(String[] args) {
        ModuleExecutor moduleExecutor = new ModuleExecutor();
        try {
            moduleExecutor.init(args);
        } catch (Exception exc) {
            LOG.log(Level.SEVERE, "Error initializing ModuleExecutor", exc);
            System.exit(EXIT_CODE_INIT_ERROR);
        }
        //now we can start corb. 
        try {
            moduleExecutor.run();
            System.exit(EXIT_CODE_SUCCESS);
        } catch (Exception exc) {
            LOG.log(Level.SEVERE, "Error while running CORB", exc);
            System.exit(EXIT_CODE_PROCESSING_ERROR);
        }
    }

    public ModuleExecutor() {

    }

    @Override
    public void init(String[] args, Properties props) throws IOException, URISyntaxException, ClassNotFoundException, InstantiationException, IllegalAccessException, XccConfigException, GeneralSecurityException {
        if (args == null) {
            args = new String[0];
        }

        if (props == null || props.isEmpty()) {
            initPropertiesFromOptionsFile();
        } else {
            this.properties = props;
        }
        initDecrypter();
        initSSLConfig();

        initURI(args.length > 0 ? args[0] : null);

        initOptions(args);

        logRuntimeArgs();

        prepareContentSource();
        registerStatusInfo();
    }

    protected void initOptions(String[] args) {
        String processModule = getOption(args.length > 1 ? args[1] : null, PROCESS_MODULE);
        String moduleRoot = getOption(args.length > 2 ? args[2] : null, MODULE_ROOT);
        String modulesDatabase = getOption(args.length > 3 ? args[3] : null, MODULES_DATABASE);
        String exportFileDir = getOption(args.length > 4 ? args[4] : null, EXPORT_FILE_DIR);
        String exportFileName = getOption(args.length > 5 ? args[5] : null, EXPORT_FILE_NAME);

        if (moduleRoot != null) {
            options.setModuleRoot(moduleRoot);
        }
        if (processModule != null) {
            options.setProcessModule(processModule);
        }
        if (modulesDatabase != null) {
            options.setModulesDatabase(modulesDatabase);
        }
        //TODO: normalize XQUERY-MODULE properties
        if (null == options.getProcessModule()) {
            throw new NullPointerException(PROCESS_MODULE + " must be specified");
        }

        if (!this.properties.containsKey(EXPORT_FILE_DIR) && exportFileDir != null) {
            this.properties.put(EXPORT_FILE_DIR, exportFileDir);
        }
        if (!this.properties.containsKey(EXPORT_FILE_NAME) && exportFileName != null) {
            this.properties.put(EXPORT_FILE_NAME, exportFileName);
        }

        if (exportFileDir != null) {
            File dirFile = new File(exportFileDir);
            if (dirFile.exists() && dirFile.canWrite()) {
                options.setExportFileDir(exportFileDir);
            } else {
                throw new IllegalArgumentException("Cannot write to export folder " + exportFileDir);
            }
        }

        // delete the export file if it exists
        if (exportFileName != null) {
            File exportFile = new File(exportFileDir, exportFileName);
            if (exportFile.exists()) {
                exportFile.delete();
            }
        }
    }

    @Override
    protected void usage() {
        PrintStream err = System.err;
        err.println("usage 1:");
        err.println("\t"
                + NAME
                + " xcc://user:password@host:port/[ database ]"
                + " process-module [module-root [modules-database [ export-file-name ] ] ]");
        err.println("\nusage 2:");
        err.println("\t"
                + "-D" + XCC_CONNECTION_URI + "=xcc://user:password@host:port/[ database ]"
                + " -D" + PROCESS_MODULE + "=module-name.xqy"
                + " -D... " + NAME);
        err.println("\nusage 3:");
        err.println("\t" + "-D" + OPTIONS_FILE + "=myjob.properties " + NAME);
        err.println("\nusage 4:");
        err.println("\t" + "-D" + OPTIONS_FILE + "=myjob.properties " + NAME
                + " xcc://user:password@host:port/[ database ]");
    }

    private void registerStatusInfo() {
        Session session = contentSource.newSession();
        AdhocQuery q = session.newAdhocQuery(XQUERY_VERSION_0_9_ML
                + DECLARE_NAMESPACE_MLSS_XDMP_STATUS_SERVER
                + "let $status := \n"
                + " xdmp:server-status(xdmp:host(), xdmp:server())\n"
                + "let $modules := $status/mlss:modules\n"
                + "let $root := $status/mlss:root\n"
                + "return (data($modules), data($root))");
        ResultSequence rs = null;
        try {
            rs = session.submitRequest(q);
        } catch (RequestException e) {
            e.printStackTrace();
        } finally {
            session.close();
        }
        while ((null != rs) && rs.hasNext()) {
            ResultItem rsItem = rs.next();
            XdmItem item = rsItem.getItem();
            if (rsItem.getIndex() == 0 && item.asString().equals("0")) {
                options.setModulesDatabase("");
            }
            if (rsItem.getIndex() == 1) {
                options.setXDBC_ROOT(item.asString());
            }
        }
        LOG.log(Level.INFO, "Configured modules db: {0}", options.getModulesDatabase());
        LOG.log(Level.INFO, "Configured modules root: {0}", options.getModuleRoot());
        LOG.log(Level.INFO, "Configured process module: {0}", options.getProcessModule());

        for (Entry<Object, Object> e : properties.entrySet()) {
            if (e.getKey() != null && !e.getKey().toString().toUpperCase().startsWith("XCC-")) {
                LOG.log(Level.INFO, "Loaded property {0}={1}", new Object[]{e.getKey(), e.getValue()});
            }
        }
    }

    public void run() throws Exception {
        LOG.log(Level.INFO, "{0} starting: {1}", new Object[]{NAME, VERSION_MSG});
        long maxMemory = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        LOG.log(Level.INFO, "maximum heap size = {0} MiB", maxMemory);

        try {
            RequestOptions opts = new RequestOptions();
            opts.setCacheResult(false);
            session = contentSource.newSession();
            Request req = null;
            List<String> propertyNames = new ArrayList<String>(properties.stringPropertyNames());
            propertyNames.addAll(System.getProperties().stringPropertyNames());
            String processModule = options.getProcessModule();
            if (isInlineOrAdhoc(processModule)) {
                String adhocQuery;
                if (isInlineModule(processModule)) {
                    adhocQuery = StringUtils.getInlineModuleCode(processModule);
                    if (isBlank(adhocQuery)) {
                        throw new IllegalStateException("Unable to read inline query ");
                    }
                    LOG.log(Level.INFO, "invoking inline process module");
                } else {
                    String queryPath = processModule.substring(0, processModule.indexOf('|'));
                    adhocQuery = getAdhocQuery(queryPath);
                    if (isBlank(adhocQuery)) {
                        throw new IllegalStateException("Unable to read adhoc query " + queryPath + " from classpath or filesystem");
                    }
                    LOG.log(Level.INFO, "invoking adhoc process module {0}", queryPath);
                }
                req = session.newAdhocQuery(adhocQuery);
                if (isJavaScriptModule(processModule)) {
                    opts.setQueryLanguage("javascript");
                }
            } else {
                String root = options.getModuleRoot();
                String modulePath = buildModulePath(root, processModule);
                LOG.log(Level.INFO, "invoking module {0}", modulePath);
                req = session.newModuleInvoke(modulePath);
            }

            // custom inputs
            for (String propName : propertyNames) {
                if (propName.startsWith(PROCESS_MODULE + ".")) {
                    String varName = propName.substring((PROCESS_MODULE + ".").length());
                    String value = getProperty(propName);
                    if (value != null) {
                        req.setNewStringVariable(varName, value);
                    }
                }
            }

            req.setOptions(opts);
            res = session.submitRequest(req);

            writeToFile(res);

            LOG.info("Done");
        } catch (Exception exc) {
            LOG.log(Level.SEVERE, exc.getMessage());
            throw exc;
        }
    }

    public String getProperty(String key) {
        String val = System.getProperty(key);
        if (isBlank(val) && properties != null) {
            val = properties.getProperty(key);
        }
        return trim(val);
    }

    private void writeToFile(ResultSequence seq) throws IOException {
        if (seq == null || !seq.hasNext()) {
            return;
        }
        String fileDir = getProperty(EXPORT_FILE_DIR);
        String fileName = getProperty(EXPORT_FILE_NAME);
        if (fileName == null || fileName.length() == 0) {
            return;
        }
        LOG.info("Writing output to file");

        BufferedOutputStream writer = null;
        try {
            File f = new File(fileDir, fileName);
            if (f.getParentFile() != null) {
                f.getParentFile().mkdirs();
            }
            writer = new BufferedOutputStream(new FileOutputStream(f));
            while (seq.hasNext()) {
                writer.write(getValueAsBytes(seq.next().getItem()));
                writer.write(NEWLINE);
            }
            writer.flush();
        } finally {
            closeQuietly(writer);
        }
    }

    protected byte[] getValueAsBytes(XdmItem item) {
        if (item instanceof XdmBinary) {
            return ((XdmBinary) item).asBinaryData();
        } else if (item != null) {
            return item.asString().getBytes();
        } else {
            return EMPTY_BYTE_ARRAY;
        }
    }

}
