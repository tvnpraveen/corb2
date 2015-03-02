Version: 2.1.0

## Running Corb

The entry point is the main method in the com.marklogic.developer.corb.Manager class. 

Corb needs one or more of the following parameters as (If specified in more then one place command line argument takes precedence over VM argument which take precedence over myjob.properties)

1. command-line arguments 
2. VM arguments ex: -DXCC-CONNECTION-URI=xcc://user:password@localhost:8202. 
3. As properties file in the class path specified using -DOPTIONS-FILE=myjob.properties. Relative and full file paths are also supported. 

Note: Any or all of the properties can be specified as java VM arguments or key value pairs in properties file.

## Options  

* XCC-CONNECTION-URI
* COLLECTION-NAME (Set to external variable URIS in the URIS module)
* XQUERY-MODULE (provide file system path if not contained in the corb package)
* THREAD-COUNT (number of worker threads; default = 1)
* MODULE-ROOT (assumes '/' if not provided)
* MODULES-DATABASE (uses the XCC-CONNECTION-URI if not provided; use 0 for filesystem)
* INSTALL (default is false; set to 'true' or '1' for installation)
* URIS-MODULE (URI selection module).
* URIS-FILE (If defined instead of URIS-MODULE, URIS will be loaded from the file located on the client)
* PROCESS-TASK (Java Class that implements com.marklogic.developer.Task or extends com.marklogic.developer.AbstractTask. It can talk to XQUERY-MODULE and do additional processing locally)    
	com.marklogic.developer.ExportBatchToFileTask (included - Writes the data returned by the XQUERY-MODULE To file specified by EXPORT-FILE-NAME)   
	com.marklogic.developer.ExportToFileTask (included - saves the document returned by XQUERY-MODULE to local file system to EXPORT-FILE-DIR where file name will be the base name of the URI)   
* PRE-BATCH-MODULE (XQuery module, if specified, will be run before batch processing starts)
* PRE-BATCH-TASK (Java Class that implements com.marklogic.developer.Task or extends com.marklogic.developer.AbstractTask. Can be specified with in in place of or in addition PRE-BATCH-MODULE)   
	com.marklogic.developer.PreBatchUpdateFileTask (included - Writes the data returned by the PRE-BATCH-MODULE to EXPORT-FILE-NAME, which can be used to writing dynamic headers. Also, if EXPORT-FILE-TOP-CONTENT is specified, this task will write this value to to the EXPORT-FILE-NAME - this option is especially useful for writing fixed headers to reports. ). 
* POST-BATCH-MODULE (XQuery module, if specified, will be run after batch processing is completed)
* POST-BATCH-TASK (Java Class that implements com.marklogic.developer.Task or extends com.marklogic.developer.AbstractTask)   
	com.marklogic.developer.PostBatchUpdateFileTask (included - Writes the data returned by the PRE-BATCH-MODULE to EXPORT-FILE-NAME. Also, if EXPORT-FILE-BOTTOM-CONTENT is specified, this task will write this value to to the EXPORT-FILE-NAME)
* EXPORT-FILE-DIR (export directory for com.marklogic.developer.corb.ExportToFileTask or similar tasks. Optional - EXPORT-FILE-NAME can be specified with full path)
* EXPORT-FILE-NAME (shared file to export output of com.marklogic.developer.corb.ExportBatchToFileTask - file name with our without full path)
* INIT-MODULE (XQuery Module, if specified, will be invoked prior to URIS-MODULE)
* INIT-TASK (Java Task, if specified, will be called prior to URIS-MODULE, This can be used addition to INIT-MODULE)

## Additional options

* EXPORT-FILE-PART-EXT (if specified, com.marklogic.developer.PreBatchUpdateFileTask adds this extension to export file. It is expected that PostBatchUpdateFileTask will be specified, which removes the extension for the final export file)
* EXPORT-FILE-TOP-CONTENT (used by com.marklogic.developer.PreBatchUpdateFileTask to insert content at the top of EXPORT-FILE-NAME before batch process starts, if it finds the text @URIS_BATCH_REF it replaces it by batch reference sent by URIS-MODULE)

* EXPORT-FILE-BOTTOM-CONTENT (used by com.marklogic.developer.PostBatchUpdateFileTask to append content to EXPORT-FILE-NAME after batch process is complete)
* EXPORT_FILE_AS_ZIP (if true, PreBatchUpdateFileTask compression the output file as a zip file)
* URIS-REPLACE-PATTERN (one or more replace patterns for URIs - typically used to save memory, but XQUERY-MODULE should reconstruct the URI. usage: URIS-REPLACE-PATTERN=pattern1,replace1,pattern2,replace2,...)
* XCC-CONNECTION-RETRY-LIMIT (Number attempts to connect to ML before giving up - default is 3)
* XCC-CONNECTION-RETRY-INTERVAL (in seconds - Time interval in seconds between retry attempts - default is 60)

## Alternate XCC connection configuration

* XCC-USERNAME (Required if XCC-CONNECTION-URI is not specified)
* XCC-PASSWORD (Required if XCC-CONNECTION-URI is not specified)
* XCC-HOSTNAME (Required if XCC-CONNECTION-URI is not specified)
* XCC-PORT (Required if XCC-CONNECTION-URI is not specified)
* XCC-DBNAME (Optional)

## Custom Inputs to XQuery modules

Any property specified with prefix (with '.') URIS-MODULE,XQUERY-MODULE,PRE-BATCH-MODULE,POST-BATCH-MODULE,INIT-MODULE, will be set as external variables to the corresponding xquery module (if defined).  
ex:  
URIS-MODULE.filePath  
XQUERY-MODULE.outputFolder

## Adhoc Tasks
INIT-MODULE, URIS-MODULE, XQUERY-MODULE, PRE-BATCH-MODULE and POST-BATCH-MODULE can be adhoc where XQuery can be local i.e. not deployed to marklogic. The xquery module should be in its named file available in classpath or filesystem.  
ex:  
PRE-BATCH-MODULE=adhoc-pre-batch.xqy|ADHOC  
INIT-MODULE=/path/to/file/adhoc-init-module.xqy|ADHOC

## Encryption

* DECRYPTER (Must extend com.marklogic.developer.corb.AbstractDecrypter. Encryptable options XCC-CONNECTION-URI, XCC-USERNAME, XCC-PASSWORD, XCC-HOST and XCC-PORT)  
  Included:  
  com.marklogic.developer.corb.JasyptDecrypter (Requires jasypt jar in classpath. Default algorithm PBEWithMD5AndTripleDES)  
  com.marklogic.developer.corb.PrivateKeyDecrypter (Requires private key file)
* JASYPT-PROPERTIES-FILE (Optional property for JasyptDecrypter. If not specified, it uses default jasypt.proeprties file, which should be accessible in classpath or file system.)  
* PRIVATE-KEY-FILE (Required property for PrivateKeyDecrypter, should be accessible in classpath or file system)
* PRIVATE-KEY-ALGORITHM (Optional for PrivateKeyDecrypter. Default is RSA)
 
### PrivateKeyDecrypter
Generate keys and encrypt the URI or password using one of the options below. Optionally, the encrypted text can be enclosed with "ENC" ex: ENC(xxxxxx)

**Java Crypt**
* java -cp marklogic-corb-2.1.*.jar com.marklogic.developer.corb.PrivateKeyDecrypter gen-keys /path/to/private.key /path/to/public.key RSA 1024 (Note: default algorithm: RSA, default key-length: 1024)
* java -cp marklogic-corb-2.1.*.jar com.marklogic.developer.corb.PrivateKeyDecrypter encrypt /path/to/public.key clearText RSA (Note: default algorithm: RSA)

**RSA keys**  
* openssl genrsa -out private.pem 1024 (generate private key in PEM format)
* openssl rsa -in private.pem -pubout > public.key  (extract public key)
* openssl pkcs8 -topk8 -nocrypt -in private.pem -out private.pkcs8.key (create PRIVATE-KEY-FILE in PKCS8 std for java)
* echo "uri or password" | openssl rsautl -encrypt -pubin -inkey public.key | base64 (encrypt URI or password. Optionally, the encrypted text can be enclosed with "ENC" ex: ENC(xxxxxx))

### JasyptDecrypter

Encrypt the URI or password as below. It is assumed that jasypt dist is available on your box. Optionally, the encrypted text can be enclosed with "ENC" ex: ENC(xxxxxx)  
jasypt-1.9.2/bin/encrypt.sh input="uri or password" password="passphrase" algorithm="algorithm" (ex: PBEWithMD5AndTripleDES or PBEWithMD5AndDES)  

**jasypt.properties file**  
jasypt.algorithm=PBEWithMD5AndTripleDES  
jasypt.password=passphrase

## Internal Properties

URIS_BATCH_REF (This is not a user specified property. URIS-MODULE can optionally send this a batch reference which can be used by post batch hooks)

## Usage

### Usage 1:
java com.marklogic.developer.corb.Manager XCC-CONNECTION-URI [COLLECTION-NAME [XQUERY-MODULE [ THREAD-COUNT [ URIS-MODULE [ MODULE-ROOT [ MODULES-DATABASE [ INSTALL [ PROCESS-TASK [ PRE-BATCH-MODULE  [ PRE-BATCH-TASK [ POST-XQUERY-MODULE  [ POST-BATCH-TASK [ EXPORT-FILE-DIR [ EXPORT-FILE-NAME [ URIS-FILE ] ] ] ] ] ] ] ] ] ] ] ] ] ] ]

### Usage 2:
java -DXCC-CONNECTION-URI=xcc://user:password@host:port/[ database ] -DXQUERY-MODULE=module-name.xqy -DTHREAD-COUNT=10 -DURIS-MODULE=get-uris.xqy -DPOST-BATCH-XQUERY-MODULE=post-batch.xqy -D... com.marklogic.developer.corb.Manager

### Usage 3:
java -DOPTIONS-FILE=myjob.properties com.marklogic.developer.corb.Manager (looks for myjob.properties file in classpath)

### Usage 4:
java -DOPTIONS-FILE=myjob.properties -DTHREAD-COUNT=10 com.marklogic.developer.corb.Manager XCC-CONNECTION-URI

##  Sample myjob.properties (Note: any of the properteis below can be specified as java VM argument i.e. '-D' option)

#### sample 1 - simple batch
XCC-CONNECTION-URI=xcc://user:password@localhost:8202/   
THREAD-COUNT=10  
MODULE-ROOT=/temp/  
MODULES-DATABASE=MY-Modules-DB   
URIS-MODULE=get-uris.xqy  
XQUERY-MODULE=SampleCorbJob.xqy  

#### sample 2 - Use username, password, host and port instead of connection URI
XCC-USERNAME=username   
XCC-PASSWORD=password   
XCC-HOSTNAME=localhost   
XCC-PORT=9999   
XCC-DBNAME=ML-database   
THREAD-COUNT=10  
MODULE-ROOT=/temp/  
MODULES-DATABASE=MY-Modules-DB   
URIS-MODULE=get-uris.xqy  
XQUERY-MODULE=SampleCorbJob.xqy 

#### sample 3 - simple batch with URIS-FILE
XCC-CONNECTION-URI=xcc://user:password@localhost:8202/   
THREAD-COUNT=10  
MODULE-ROOT=/temp/  
MODULES-DATABASE=MY-Modules-DB   
URIS-FILE=input-uris.csv  
XQUERY-MODULE=SampleCorbJob.xqy  

#### sample 4 - report
...  
XQUERY-MODULE=get-data-from-document.xqy   
PROCESS-TASK=com.marklogic.developer.corb.ExportBatchToFileTask  
EXPORT-FILE-DIR=/temp/export  
EXPORT-FILE-NAME=myfile.csv   

#### sample 5 - report with header
...   
XQUERY-MODULE=get-data-from-document.xqy   
PROCESS-TASK=com.marklogic.developer.corb.ExportBatchToFileTask  
EXPORT-FILE-DIR=/temp/export  
EXPORT-FILE-NAME=myfile.csv
PRE-BATCH-TASK=com.marklogic.developer.corb.PreBatchUpdateFileTask  
EXPORT-FILE-TOP-CONTENT=col1,col2,col3  

#### sample 6 - dynamic headers
...      
EXPORT-FILE-NAME=/temp/export/myfile.csv   
PRE-BATCH-MODULE=pre-batch-header.xqy 
PRE-BATCH-TASK=com.marklogic.developer.corb.PreBatchUpdateFileTask   

#### sample 7 - pre and post batch hooks
...   
PRE-BATCH-MODULE=pre-batch.xqy   
POST-BATCH-MODULE=post-batch.xqy   

#### sample 8 - adhoc tasks (xquery modules live local to filesystem where corb is located. Any xquery module can be adhoc)
...   
XQUERY-MODULE=SampleCorbJob.xqy   
PRE-BATCH-MODULE=/local/path/to/adhoc-pre-batch.xqy|ADHOC

#### sample 9 - jasypt encryption (Any XCC-* property can be encrypted and optionally enclosed by ENC() ex: XCC-CONNECTION-URI or XCC-PASSWORD. If JASYPT-PROPERTIES-FILE is not specified, it assumes default jasypt.properties)
XCC-CONNECTION-URI=ENC(encrypted_uri) 
...   
DECRYPTER=com.marklogic.developer.corb.JasyptDecrypter  

**sample jasypt.properties**   
jasypt.password=foo   
jasypt.algorithm=PBEWithMD5AndTripleDES  

#### sample 10 - private key encryption with java keys (Any XCC-* property can be encrypted and optionally enclosed by ENC() ex: XCC-CONNECTION-URI or XCC-PASSWORD)
XCC-CONNECTION-URI=encrypted_uri  
...   
DECRYPTER=com.marklogic.developer.corb.PrivateKeyDecrypter  
PRIVATE-KEY-FILE=/path/to/key/private.key  
PRIVATE-KEY-ALGORITHM=RSA  

#### sample 11 - private key encryption with unix keys (Any XCC-* property can be encrypted and optionally enclosed by ENC() ex: XCC-CONNECTION-URI or XCC-PASSWORD)
XCC-CONNECTION-URI=encrypted_uri  
...   
DECRYPTER=com.marklogic.developer.corb.PrivateKeyDecrypter  
PRIVATE-KEY-FILE=/path/to/rsa/key/rivate.pkcs8.key  
