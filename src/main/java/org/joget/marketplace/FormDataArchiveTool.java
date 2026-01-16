package org.joget.marketplace;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jayway.jsonpath.JsonPath;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.io.FileUtils;
import org.joget.apps.app.dao.AppDefinitionDao;
import org.joget.apps.app.dao.FormDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.FormDefinition;
import org.joget.apps.app.service.AppPluginUtil;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.dao.FormDataDaoImpl;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.service.FileUtil;
import org.joget.apps.form.service.FormService;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.FileManager;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.PluginThread;
import org.joget.commons.util.SetupManager;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.plugin.base.PluginWebSupport;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.WorkflowProcess;
import org.joget.workflow.model.WorkflowProcessLink;
import org.joget.workflow.model.dao.WorkflowProcessLinkDao;
import org.joget.workflow.model.service.WorkflowManager;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.context.ApplicationContext;

public class FormDataArchiveTool extends DefaultApplicationPlugin implements PluginWebSupport {

    private final static String MESSAGE_PATH ="messages/formDataArchiveTool";

    @Override
    public Object execute(Map map) {
        ApplicationContext ac = AppUtil.getApplicationContext();
        AppService appService = (AppService) ac.getBean("appService");
        // AppDefinition currentAppDef = AppUtil.getCurrentAppDefinition();

        FormDataDao formDataDao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
        FormDefinitionDao formDefinitionDao = (FormDefinitionDao) FormUtil.getApplicationContext().getBean("formDefinitionDao");
        // DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
        WorkflowManager workflowManager = (WorkflowManager) AppUtil.getApplicationContext().getBean("workflowManager");
        WorkflowProcessLinkDao linkDao = (WorkflowProcessLinkDao) AppUtil.getApplicationContext().getBean("workflowProcessLinkDao");

        
        String archiveFrontendBackendProperty = getPropertyString("archiveFrontendBackend");
        String selectedAppProperty = getPropertyString("selectedApp");
        String selectedFormsProperty = getPropertyString("selectedForms");
        String startDateProperty = getPropertyString("startDate");
        String endDateProperty = getPropertyString("endDate");
        String childFormArchiveProperty = getPropertyString("childFormArchive");
        String deleteTableDataProperty = getPropertyString("deleteTableData");
        String deleteFilesProperty = getPropertyString("deleteFiles");
        String deleteProcessesProperty = getPropertyString("deleteProcesses");
        String pathOptions = getPropertyString("pathOptions");
        String fileFormDefId = getPropertyString("fileFormDefId");
        String fileFieldId = getPropertyString("fileFieldId");
        String archivePathProperty = getPropertyString("archivePath");

        // get value from frontend
        String formDefIdProperty = (String) properties.get("formDefId");
        String appProperty = (String) properties.get("appField");
        String formProperty = (String) properties.get("formField");
        String childFormArchivePropertyFrontend = getPropertyString("childFormArchiveFrontend");
        String deleteTableDataPropertyFrontend = getPropertyString("deleteTableDataFrontend");
        String deleteFilesPropertyFrontend = getPropertyString("deleteFilesFrontend");
        String deleteProcessesPropertyFrontend = getPropertyString("deleteProcessesFrontend");
        String pathOptionsFrontend = getPropertyString("pathOptionsFrontend");
        String fileFormDefIdFrontend = getPropertyString("fileFormDefIdFrontend");
        String fileFieldIdFrontend = getPropertyString("fileFieldIdFrontend");
        String archivePathPropertyFrontend = getPropertyString("archivePathFrontend");
        
        if (archiveFrontendBackendProperty.equals("frontend")){
            AppDefinition currentAppDef = AppUtil.getCurrentAppDefinition();
            String appId = currentAppDef.getAppId();
            String appVersion = String.valueOf(currentAppDef.getVersion());
            String recordId = "";
            WorkflowAssignment wfAssignment = (WorkflowAssignment) properties.get("workflowAssignment");
            if (wfAssignment != null) {
                recordId = appService.getOriginProcessId(wfAssignment.getProcessId());
            } else {
                recordId = (String) properties.get("recordId");
            }
            FormRowSet rows = appService.loadFormData(appId, appVersion, formDefIdProperty, recordId);
            FormRow formRow = rows.get(0);

            selectedAppProperty = (String) formRow.get(appProperty);
            selectedFormsProperty = (String) formRow.get(formProperty);
            childFormArchiveProperty = (String) formRow.get(childFormArchivePropertyFrontend);
            deleteTableDataProperty = (String) formRow.get(deleteTableDataPropertyFrontend);
            deleteFilesProperty = (String) formRow.get(deleteFilesPropertyFrontend);
            deleteProcessesProperty = (String) formRow.get(deleteProcessesPropertyFrontend);
            pathOptions = (String) formRow.get(pathOptionsFrontend);
            fileFormDefId = (String) formRow.get(fileFormDefIdFrontend);
            fileFieldId = (String) formRow.get(fileFieldIdFrontend);
            archivePathProperty = (String) formRow.get(archivePathPropertyFrontend);
        }

        // Create final variables for use in lambda (must be effectively final)
        final String finalPathOptions = pathOptions;
        final String finalFileFormDefId = fileFormDefId;
        final String finalFileFieldId = fileFieldId;
        final String finalArchivePathProperty = archivePathProperty;

        // validate the archive path
        if("FILE_PATH".equalsIgnoreCase(finalPathOptions)){       
            File archiveDir = new File(finalArchivePathProperty);
            if (!archiveDir.exists() || !archiveDir.isDirectory()) {
                LogUtil.info(getClassName(), "Backup path does not exist or is not a directory: " + finalArchivePathProperty);
                return null;
            }
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        sdf.setLenient(false);

        Date startDate, endDate;
        try {
            startDate = sdf.parse(startDateProperty);
        } catch (Exception e) {
            LogUtil.info(getClassName(), "Invalid start date format. Expected yyyy-MM-dd: " + startDateProperty);
            return null;
        }

        try {
            endDate = sdf.parse(endDateProperty);
        } catch (Exception e) {
            LogUtil.info(getClassName(), "Invalid end date format. Expected yyyy-MM-dd: " + endDateProperty);
            return null;
        }

        if (startDate.after(endDate)) {
            LogUtil.info(getClassName(), "Start date cannot be after end date.");
            return null;
        }

        AppDefinition selectedAppDef = appService.getPublishedAppDefinition(selectedAppProperty);

        Set<String> formDefinitionIdSet = new LinkedHashSet<>();
        if (selectedFormsProperty != null && !selectedFormsProperty.isEmpty()) {
            String[] pieces = selectedFormsProperty.split(";");
            for (String formDefId : pieces) {
                FormDefinition formDef = formDefinitionDao.loadById(formDefId, selectedAppDef);
                formDefinitionIdSet.add(formDefId);

                if ("true".equalsIgnoreCase(childFormArchiveProperty)) {
                    String formJson = formDef.getJson();
                    List<String> formGridFormDefIds = JsonPath.read(formJson, "$..[?(@.className=='org.joget.plugin.enterprise.FormGrid')].properties.storeBinder.properties.formDefId");
                    List<String> spreadSheetFormDefIds = JsonPath.read(formJson, "$..[?(@.className=='org.joget.plugin.enterprise.SpreadSheetGrid')].properties.storeBinder.properties.formDefId");

                    if (formGridFormDefIds != null && !formGridFormDefIds.isEmpty()) {
                        formDefinitionIdSet.addAll(formGridFormDefIds);
                    }

                    if (spreadSheetFormDefIds != null && !spreadSheetFormDefIds.isEmpty() ) {
                        formDefinitionIdSet.addAll(spreadSheetFormDefIds);
                    }
                }
            }
        }

        List<String> formDefinitionIds = new ArrayList<>(formDefinitionIdSet);
        boolean deleteFiles = "true".equalsIgnoreCase(deleteFilesProperty);
        boolean deleteProcesses = "true".equalsIgnoreCase(deleteProcessesProperty);
        boolean deleteTableData = "true".equalsIgnoreCase(deleteTableDataProperty);

        Calendar endCal = Calendar.getInstance();
        endCal.setTime(endDate);
        endCal.set(Calendar.HOUR_OF_DAY, 23);
        endCal.set(Calendar.MINUTE, 59);
        endCal.set(Calendar.SECOND, 59);
        endCal.set(Calendar.MILLISECOND, 999);
        endDate = endCal.getTime();

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String startDateStr = dateFormat.format(startDate);
        String endDateStr = dateFormat.format(endDate);
        Timestamp startTs = Timestamp.valueOf(startDateStr);
        Timestamp endTs = Timestamp.valueOf(endDateStr);

        if (0 < 1) {
            Thread archiveThread = new PluginThread(() -> {
                Set<String> successfulArchivedTables = new HashSet<>();
                Map<String, String> filesToDelete = new HashMap<>();
                Map<String, File> generatedFiles = new HashMap<>();
                Map<String, String> formDefIdToTableName = new HashMap<>();
                Map<String, List<String>> tableNameToDeletedIds = new HashMap<>();
                Map<String, Long> tableNameToTotalRows = new HashMap<>();
                Map<String, Long> tableNameToArchivedRows = new HashMap<>();

                boolean errorOccurred = false;
                
                // Determine base path for archiving based on pathOptions
                String baseArchivePath;
                if ("FORM_FIELD".equalsIgnoreCase(finalPathOptions)) {
                    // Use FileManager base directory when saving to form field
                    baseArchivePath = FileManager.getBaseDirectory() + "fda_temp_" + System.currentTimeMillis();
                    try {
                        FileUtils.forceMkdir(new File(baseArchivePath));
                    } catch (IOException e) {
                        LogUtil.error(getClassName(), e, "Failed to create temporary archive directory: " + baseArchivePath);
                        errorOccurred = true;
                    }
                } else {
                    // Use provided archive path
                    baseArchivePath = finalArchivePathProperty;
                }

                for (String formDefId : formDefinitionIds) {
                    String tableName = appService.getFormTableName(selectedAppDef, formDefId);
                    formDefIdToTableName.put(tableName, formDefId);
                    tableNameToDeletedIds.put(tableName, new ArrayList<>());

                    //Collection<String> columnNamesa = formDataDao.getFormDefinitionColumnNames(tableName);

                    boolean isDx9 = false;
                    try {
                        Class dx9Class = Class.forName("org.joget.commons.util.DynamicCacheElement");
                        isDx9 = true;
                    } catch (Exception be) {
                        // ignore
                    }
                    FormRowSet rows = null;
                    if(isDx9){
                        rows = formDataDao.find(formDefId, tableName, "where e.dateCreated >= ? AND e.dateCreated <= ?", new Object[]{startTs, endTs}, null, null, 0, 1);
                    } else{
                        rows = formDataDao.find(formDefId, tableName, "where e.dateCreated >= ? AND e.dateCreated <= ?", new Object[]{startDateStr, endDateStr}, null, null, 0, 1);
                    }

                    List<String> columnNames = new ArrayList<>();
                    List<String> defaultColumns = Arrays.asList("id", "dateCreated", "dateModified", "createdBy", "createdByName", "modifiedBy", "modifiedByName");

                    if (rows != null && !rows.isEmpty()) {
                        Map<String, String> tableProperties = rows.get(0).getCustomProperties();
                        Set<String> allKeys = tableProperties.keySet();

                        for (String col : defaultColumns) {
                            if (allKeys.contains(col)) {
                                columnNames.add(col);
                            }
                        }

                        for (String col : allKeys) {
                            if (!defaultColumns.contains(col)) {
                                columnNames.add(col);
                            }
                        }
                    } else {
                        LogUtil.error(getClassName(), null, "No records found to archive.");
                        break;
                    }

                    long totalRows = 0;
                    if(isDx9){
                        totalRows = formDataDao.count(formDefId, tableName, "where e.dateCreated >= ? AND e.dateCreated <= ?", new Object[]{startTs, endTs});
                    } else{
                        totalRows = formDataDao.count(formDefId, tableName, "where e.dateCreated >= ? AND e.dateCreated <= ?", new Object[]{startDateStr, endDateStr});                    
                    }
                    tableNameToTotalRows.put(tableName, totalRows);

                    if (totalRows > 0) {
                        if (errorOccurred) {
                            break;
                        }
                        String dataArchivedPath = baseArchivePath + File.separator + tableName;
                        boolean isFolderCreated = createOrOverwriteArchiveFolder(dataArchivedPath);
                        if (!isFolderCreated) {
                            errorOccurred = true;
                            LogUtil.error(getClassName(), null, "Failed to create archive folder for: " + dataArchivedPath);
                            break;
                        }

                        String tableNameWithPreFixed = FormDataDaoImpl.FORM_PREFIX_TABLE_NAME + tableName;
                        LogUtil.info(getClassName(), "Started archiving form: " + formDefId + " at " + new Date());
                        String csvFile = dataArchivedPath + File.separator + tableNameWithPreFixed + ".csv";

                        try (FileWriter csvWriter = new FileWriter(csvFile); CSVPrinter csvPrinter = new CSVPrinter(csvWriter, CSVFormat.DEFAULT.withHeader(columnNames.toArray(new String[0])))) {
                            File csvFileObj = new File(csvFile);
                            generatedFiles.put(csvFile, csvFileObj);

                            int batchSize = 1000;
                            int totalBatches = (int) Math.ceil((double) totalRows / batchSize);

                            for (int batch = 0; batch < totalBatches; batch++) {
                                int start = batch * batchSize;
                                FormRowSet batchRows = null;
                                if(isDx9){
                                    batchRows = formDataDao.find(formDefId, tableName, "where e.dateCreated >= ? AND e.dateCreated <= ?", new Object[]{startTs, endTs}, null, null, start, batchSize);
                                } else{
                                    batchRows = formDataDao.find(formDefId, tableName, "where e.dateCreated >= ? AND e.dateCreated <= ?", new Object[]{startDateStr, endDateStr}, null, null, start, batchSize);
                                }
                                if (batchRows != null && !batchRows.isEmpty()) {
                                    for (FormRow row : batchRows) {
                                        tableNameToDeletedIds.get(tableName).add(row.getId());

                                        List<String> csvValues = new ArrayList<>();
                                        for (String col : columnNames) {
                                            String val = row.getProperty(col);
                                            csvValues.add(val != null ? val.replace("'", "''") : "");
                                        }
                                        csvPrinter.printRecord(csvValues);

                                        String id = row.getId();
                                        String formUploadPath = FileUtil.getUploadPath(tableName, id);
                                        File sourceDir = new File(formUploadPath);
                                        if (sourceDir.exists()) {
                                            File destDir = new File(dataArchivedPath + File.separator + id);
                                            FileUtils.copyDirectory(sourceDir, destDir);
                                            if (deleteFiles) {
                                                filesToDelete.put(id, sourceDir.getAbsolutePath());
                                            }
                                        }
                                    }
                                }
                            }
                            successfulArchivedTables.add(tableNameWithPreFixed);
                            LogUtil.info(getClassName(), "Data archive complete for table: " + tableNameWithPreFixed);
                            tableNameToArchivedRows.put(tableName, (long) tableNameToDeletedIds.get(tableName).size());
                        } catch (IOException ex) {
                            Logger.getLogger(FormDataArchiveTool.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                }

                if (!errorOccurred && !successfulArchivedTables.isEmpty()) {
                    // Generate timestamp for filename
                    SimpleDateFormat dateFormatZip = new SimpleDateFormat("yyyyMMddHHmmss");
                    String timestamp = dateFormatZip.format(new Date());
                    String finalZipFileName = "archived-data-" + timestamp + ".zip";
                    
                    // Determine where to create the ZIP file
                    File finalZipFile;
                    if ("FORM_FIELD".equalsIgnoreCase(finalPathOptions)) {
                        // Create ZIP in FileManager base directory when saving to form field
                        finalZipFile = new File(FileManager.getBaseDirectory() + finalZipFileName);
                    } else {
                        // Create ZIP in archive path for FILE_PATH option
                        String finalZipFilePath = finalArchivePathProperty + File.separator + finalZipFileName;
                        finalZipFile = new File(finalZipFilePath);
                    }

                    try {
                        // Create ZIP file
                        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(finalZipFile));

                        // Add table folders to ZIP
                        for (String tableName : formDefIdToTableName.keySet()) {
                            File tableArchiveDir = new File(baseArchivePath + File.separator + tableName);
                            if (tableArchiveDir.exists()) {
                                addDirToZip(zos, tableArchiveDir, tableName);
                            }
                        }
                        zos.close();

                        LogUtil.info(getClassName(), "Final archive ZIP created: " + finalZipFile.getAbsolutePath() + " containing " + successfulArchivedTables.size() + " tables");

                        // Handle file saving based on pathOptions
                        if ("FILE_PATH".equalsIgnoreCase(finalPathOptions)) {
                            if (finalZipFile.exists()) {
                                LogUtil.info(getClassName(), "File saved to: " + finalArchivePathProperty);
                            }
                        } else if ("FORM_FIELD".equalsIgnoreCase(finalPathOptions)) {
                            if (finalZipFile.exists()) {
                                // Generate a new record ID for storing the file
                                String recordId = UUID.randomUUID().toString();
                                try {
                                    saveFileToForm(appService, selectedAppDef, finalFileFormDefId, finalFileFieldId, finalZipFile, recordId);
                                    LogUtil.info(getClassName(), "ZIP file saved to form field: " + finalFileFieldId + " in form: " + finalFileFormDefId);
                                    // Clean up temporary ZIP file after saving to form
                                    try {
                                        if (finalZipFile.exists()) {
                                            finalZipFile.delete();
                                        }
                                    } catch (Exception e) {
                                        LogUtil.warn(getClassName(), "Failed to delete temporary ZIP file: " + finalZipFile.getAbsolutePath());
                                    }
                                } catch (Exception e) {
                                    LogUtil.error(getClassName(), e, "Failed to save ZIP file to form field");
                                    errorOccurred = true;
                                }
                            }
                        }

                        // Delete archived table folders after ZIP creation
                        for (String tableName : formDefIdToTableName.keySet()) {
                            File tableArchiveDir = new File(baseArchivePath + File.separator + tableName);
                            if (tableArchiveDir.exists()) {
                                try {
                                    FileUtils.deleteDirectory(tableArchiveDir);
                                } catch (IOException e) {
                                    LogUtil.error(getClassName(), e, "Failed to delete table archive folder: " + tableArchiveDir.getAbsolutePath());
                                }
                            }
                        }
                        
                        // Clean up temporary base directory if using FORM_FIELD
                        if ("FORM_FIELD".equalsIgnoreCase(finalPathOptions)) {
                            try {
                                File baseDir = new File(baseArchivePath);
                                if (baseDir.exists()) {
                                    FileUtils.deleteDirectory(baseDir);
                                }
                            } catch (IOException e) {
                                LogUtil.warn(getClassName(), "Failed to delete temporary archive directory: " + baseArchivePath);
                            }
                        }

                    } catch (IOException e) {
                        LogUtil.error(getClassName(), e, "Failed to create final archive ZIP: " + finalZipFile.getAbsolutePath());
                        errorOccurred = true;
                    }
                }

                if (!errorOccurred && !successfulArchivedTables.isEmpty()) {
                    // Step 1: Delete completed processes (placeholder for your logic)
                    if (deleteProcesses) {
                        try {
                            for (Map.Entry<String, List<String>> entry : tableNameToDeletedIds.entrySet()) {
                                List<String> deletedIds = entry.getValue();
                                for (String recordId : deletedIds) {
                                    Collection<WorkflowProcessLink> processLinks = linkDao.getLinks(recordId);
                                    if (processLinks != null && !processLinks.isEmpty()) {
                                        for (WorkflowProcessLink l : processLinks) {
                                            try {
                                                WorkflowProcess process = workflowManager.getRunningProcessById(l.getProcessId());
                                                if (process != null && process.getInstanceId() != null) {
                                                    String processId = l.getProcessId();
                                                    if (process.getState().startsWith("closed")) {
                                                        workflowManager.removeProcessInstance(processId);
                                                    }
                                                }
                                            } catch (Exception e) {
                                                // do nothing
                                            }
                                        }
                                    }
                                }
                            }
                            LogUtil.info(getClassName(), "Process deletion logic executed (placeholder)");
                        } catch (Exception ex) {
                            errorOccurred = true;
                        }
                    }

                    // Step 2: Delete files
                    if (!errorOccurred && deleteFiles && !filesToDelete.isEmpty()) {
                        for (Map.Entry<String, String> entry : filesToDelete.entrySet()) {
                            try {
                                File fileToDelete = new File(entry.getValue());
                                if (fileToDelete.exists()) {
                                    FileUtils.deleteDirectory(fileToDelete);
                                }
                            } catch (IOException ex) {
                                errorOccurred = true;
                                break;
                            }
                        }
                    }

                    // Step 3: Delete table data
                    if (deleteTableData) {
                        if (!errorOccurred) {
                            try {
                                for (String tableName : successfulArchivedTables) {
                                    // Remove prefix to get actual tableName
                                    String rawTableName = tableName.replaceFirst(FormDataDaoImpl.FORM_PREFIX_TABLE_NAME, "");
                                    String formDefId = formDefIdToTableName.get(rawTableName);
                                    List<String> allIds = tableNameToDeletedIds.get(rawTableName);

                                    if (formDefId != null && allIds != null && !allIds.isEmpty()) {
                                        int batchSize = 1000;
                                        for (int i = 0; i < allIds.size(); i += batchSize) {
                                            List<String> batchIds = allIds.subList(i, Math.min(i + batchSize, allIds.size()));
                                            formDataDao.delete(formDefId, rawTableName, batchIds.toArray(new String[0]));
                                            LogUtil.info(getClassName(), "Deleted " + batchIds.size() + " rows from " + rawTableName);
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                LogUtil.error(getClassName(), e, "Error during Joget DAO deletion.");
                                errorOccurred = true;
                            }
                        }

                    }
                }
            });
            archiveThread.setDaemon(true);
            archiveThread.start();
        }

        return null;
    }

    private void addDirToZip(ZipOutputStream zos, File dir, String dirName) throws IOException {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    addDirToZip(zos, file, dirName + "/" + file.getName());
                } else {
                    ZipEntry entry = new ZipEntry(dirName + "/" + file.getName());
                    zos.putNextEntry(entry);
                    Files.copy(file.toPath(), zos);
                    zos.closeEntry();
                }
            }
        }
    }

    
    // this might not be needed any more
    private Set<String> writeDataToCsv(ResultSet rs, String csvFile) throws SQLException, IOException {
        Set<String> idSet = new HashSet<>();
        try (FileWriter writer = new FileWriter(csvFile)) {
            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();

            // Write CSV header
            for (int i = 1; i <= columnCount; i++) {
                writer.write(meta.getColumnName(i));
                if (i < columnCount) {
                    writer.write(",");
                }
            }
            writer.write("\n");

            // Write data
            while (rs.next()) {
                String id = rs.getString("id");
                idSet.add(id);  // Store the IDß
                for (int i = 1; i <= columnCount; i++) {
                    Object value = rs.getObject(i);
                    writer.write(value != null ? value.toString() : "");
                    if (i < columnCount) {
                        writer.write(",");
                    }
                }
                writer.write("\n");
            }
        }
        return idSet;
    }

    private boolean createOrOverwriteArchiveFolder(String dataArchivedPath) {
        File archiveDir = new File(dataArchivedPath);

        try {
            if (archiveDir.exists()) {
                FileUtils.deleteDirectory(archiveDir);
            }
            FileUtils.forceMkdir(archiveDir);
            return archiveDir.exists();
        } catch (IOException e) {
            LogUtil.error(getClassName(), e, "Failed to create or overwrite archive directory: " + dataArchivedPath);
            return false;
        }
    }

    private void saveFileToForm(AppService appService, AppDefinition appDef, String fileFormDefId, String fileFieldId, File outputFile, String recordId){
        if (outputFile.exists()) {
            String fileName = outputFile.getName();
            String tableName = fileFormDefId;
            FileUtil.storeFile(outputFile, tableName, recordId);
            FormRowSet rows = new FormRowSet();
            FormRow row = new FormRow();
            row.put(fileFieldId, fileName);
            rows.add(row);
            appService.storeFormData(fileFormDefId, tableName, rows, recordId);
        }
    }

    protected Form getForm(AppDefinition appDef, String formDefId) {
        FormService formService = (FormService) AppUtil.getApplicationContext().getBean("formService");
        FormDefinitionDao formDefinitionDao = (FormDefinitionDao) AppUtil.getApplicationContext().getBean("formDefinitionDao");

        Form form = null;
        FormDefinition formDef = formDefinitionDao.loadById(formDefId, appDef);

        if (formDef != null && formDef.getJson() != null) {
            String formJson = formDef.getJson();
            form = (Form) formService.createElementFromJson(formJson);
        }

        return form;
    }

    public void extractFormDefIds(JsonElement element, List<String> formDefIds) {
        if (element == null || element.isJsonNull()) {
            return;
        }

        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();

            // Check if it's a FormGrid
            if (obj.has("className") && "org.joget.plugin.enterprise.FormGrid".equals(obj.get("className").getAsString())) {
                JsonObject props = obj.getAsJsonObject("properties");
                if (props != null && props.has("formDefId")) {
                    formDefIds.add(props.get("formDefId").getAsString());
                }
            }

            // Recurse into all object members
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                extractFormDefIds(entry.getValue(), formDefIds);
            }

        } else if (element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                extractFormDefIds(item, formDefIds);
            }
        }
    }

    public void extractFormDefIds(JSONArray elements, JSONArray resultList) {
        for (int i = 0; i < elements.length(); i++) {
            JSONObject element = elements.getJSONObject(i);

            if (element.has("storeBinder")) {
                JSONObject storeBinder = element.getJSONObject("storeBinder");
                if (storeBinder.has("properties")) {
                    JSONObject props = storeBinder.getJSONObject("properties");
                    if (props.has("formDefId")) {
                        resultList.put(props.getString("formDefId"));
                    }
                }
            }

            if (element.has("elements")) {
                extractFormDefIds(element.getJSONArray("elements"), resultList);
            }
        }
    }

    protected String getFormMappingPath() {
        // determine path to mapping directory
        String path = SetupManager.getBaseDirectory();
        if (!path.endsWith(File.separator)) {
            path += File.separator;
        }
        path += FormDataDaoImpl.FORM_MAPPING_DIRECTORY;
        new File(path).mkdirs();
        return path;
    }

    // private void createSummaryFile(String archivePathProperty,
    //         String selectedAppProperty,
    //         String selectedFormsProperty,
    //         String startDateProperty,
    //         String endDateProperty,
    //         String childFormArchiveProperty,
    //         String generateSqlDumpFileProperty,
    //         String deleteFilesProperty,
    //         String deleteProcessesProperty,
    //         Map<String, String> formDefIdToTableName,
    //         Map<String, Long> tableNameToTotalRows,
    //         Map<String, Long> tableNameToArchivedRows) {

    //     String summaryFilePath = archivePathProperty + File.separator + "summary.txt";

    //     try (BufferedWriter writer = new BufferedWriter(new FileWriter(summaryFilePath))) {
    //         // Header information
    //         writer.write("=== ARCHIVE SUMMARY ===");
    //         writer.newLine();
    //         writer.newLine();

    //         SimpleDateFormat fullDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    //         writer.write("Current Date And Time: " + fullDateFormat.format(new Date()));
    //         writer.newLine();
    //         writer.newLine();

    //         // Configuration details
    //         writer.write("CONFIGURATION:");
    //         writer.newLine();
    //         writer.write("Selected App: " + (selectedAppProperty != null ? selectedAppProperty : "N/A"));
    //         writer.newLine();
    //         writer.write("Selected Forms: " + (selectedFormsProperty != null ? selectedFormsProperty.replace(";", ", ") : "N/A"));
    //         writer.newLine();
    //         writer.write("Start Date: " + (startDateProperty != null ? startDateProperty : "N/A"));
    //         writer.newLine();
    //         writer.write("End Date: " + (endDateProperty != null ? endDateProperty : "N/A"));
    //         writer.newLine();
    //         writer.write("Child Form Archive: " + (childFormArchiveProperty != null ? childFormArchiveProperty : "false"));
    //         writer.newLine();
    //         writer.write("Generate SQL Dump: " + (generateSqlDumpFileProperty != null ? generateSqlDumpFileProperty : "false"));
    //         writer.newLine();
    //         writer.write("Delete Files: " + (deleteFilesProperty != null ? deleteFilesProperty : "false"));
    //         writer.newLine();
    //         writer.write("Delete Processes: " + (deleteProcessesProperty != null ? deleteProcessesProperty : "false"));
    //         writer.newLine();
    //         writer.write("Archive Path: " + (archivePathProperty != null ? archivePathProperty : "N/A"));
    //         writer.newLine();
    //         writer.newLine();

    //         // Table summary
    //         writer.write("TABLES ARCHIVED:");
    //         writer.newLine();
    //         writer.write("=================");
    //         writer.newLine();

    //         long totalQueriedRows = 0;
    //         long totalArchivedRows = 0;

    //         for (Map.Entry<String, String> entry : formDefIdToTableName.entrySet()) {
    //             String tableName = entry.getKey();
    //             String formDefId = entry.getValue();

    //             Long queriedRows = tableNameToTotalRows.getOrDefault(tableName, 0L);
    //             Long archivedRows = tableNameToArchivedRows.getOrDefault(tableName, 0L);

    //             totalQueriedRows += queriedRows;
    //             totalArchivedRows += archivedRows;

    //             writer.write("Table Name: " + tableName);
    //             writer.newLine();
    //             writer.write("Form Definition ID: " + formDefId);
    //             writer.newLine();
    //             writer.write("Total Rows Queried: " + queriedRows);
    //             writer.newLine();
    //             writer.write("Total Rows Archived: " + archivedRows);
    //             writer.newLine();
    //             writer.write("---");
    //             writer.newLine();
    //         }

    //         writer.newLine();
    //         writer.write("OVERALL SUMMARY:");
    //         writer.newLine();
    //         writer.write("================");
    //         writer.newLine();
    //         writer.write("Total Tables Processed: " + formDefIdToTableName.size());
    //         writer.newLine();
    //         writer.write("Total Rows Queried: " + totalQueriedRows);
    //         writer.newLine();
    //         writer.write("Total Rows Archived: " + totalArchivedRows);
    //         writer.newLine();

    //         LogUtil.info(getClassName(), "Summary file created successfully: " + summaryFilePath);

    //     } catch (IOException e) {
    //         LogUtil.error(getClassName(), e, "Failed to create summary file: " + summaryFilePath);
    //     }
    // }

    // public void processFilesForRow() {

    // }

    @Override
    public String getName() {
        return AppPluginUtil.getMessage("org.joget.marketplace.FormDataArchiveTool.label", getClassName(), MESSAGE_PATH);   
    }

    @Override
    public String getVersion() {
        return "8.0.1";
    }

    @Override
    public String getDescription() {
        return AppPluginUtil.getMessage("org.joget.marketplace.FormDataArchiveTool.desc", getClassName(), MESSAGE_PATH);   
    }

    @Override
    public String getLabel() {
        return AppPluginUtil.getMessage("org.joget.marketplace.FormDataArchiveTool.label", getClassName(), MESSAGE_PATH);   
    }

    @Override
    public String getClassName() {
        return this.getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClass().getName(), "/properties/formDataArchiveTool.json", null, true, MESSAGE_PATH);
    }

    @Override
    public void webService(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        if ("getApps".equals(request.getParameter("action"))) {
            AppDefinitionDao appDefinitionDao = (AppDefinitionDao) AppUtil.getApplicationContext().getBean("appDefinitionDao");
            Collection<AppDefinition> appDefinitionList = appDefinitionDao.findLatestVersions(null, null, null, "name", false, null, null);
            JSONArray jsonArray = new JSONArray();
            for (AppDefinition appDef : appDefinitionList) {
                Map<String, String> option = new HashMap<>();
                //option.put("value", p.getIdWithoutVersion());
                option.put("value", appDef.getAppId());
                option.put("label", appDef.getName());
                jsonArray.put(option);
            }
            jsonArray.write(response.getWriter());
            return;
        } else if ("getForms".equals(request.getParameter("action"))) {

            AppService appService = (AppService) AppUtil.getApplicationContext().getBean("appService");
            FormDefinitionDao formDefinitionDao = (FormDefinitionDao) AppUtil.getApplicationContext().getBean("formDefinitionDao");
            Collection<FormDefinition> formDefinitionList = null;

            // get the current app def
            AppDefinition currentAppDef = AppUtil.getCurrentAppDefinition();

            String selectedApp = request.getParameter("selectedApp");
            AppDefinition selectedAppDef = appService.getPublishedAppDefinition(selectedApp);

            if (selectedAppDef != null) {
                formDefinitionList = formDefinitionDao.getFormDefinitionList(null, selectedAppDef, null, null, 0, 999);
            } else {
                formDefinitionList = new ArrayList<FormDefinition>();
            }

            JSONArray jsonArray = new JSONArray();
            Map blank = new HashMap();
            blank.put("value", "");
            blank.put("label", "");
            jsonArray.put(blank);
            for (FormDefinition formDef : formDefinitionList) {
                Map data = new HashMap();
                data.put("value", formDef.getId());
                data.put("label", formDef.getName());
                jsonArray.put(data);
            }

            // set back the current app def
            AppUtil.setCurrentAppDefinition(currentAppDef);

            jsonArray.write(response.getWriter());
            return;
        }
    }
}
