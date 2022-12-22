@Grab(group = 'org.codehaus.groovy.modules.http-builder', module = 'http-builder', version = '0.7')
@Grab('org.apache.commons:commons-lang3:3.3.2')
@Grab('com.xlson.groovycsv:groovycsv:1.0')

import groovyx.net.http.RESTClient
import groovy.xml.MarkupBuilder

import java.text.SimpleDateFormat

import static org.apache.commons.lang3.StringEscapeUtils.unescapeHtml4
import static com.xlson.groovycsv.CsvParser.parseCsv
import static groovyx.net.http.ContentType.JSON

class MangroveWatchImport {


    static void main(args) {
        def cli = new CliBuilder(usage: "groovy MangroveWatchImport -f <datafile> -o opusId -p <profileServiceBaseUrl> -d <delimiter default ~> -r <reportfile>")
        cli.f(longOpt: "file", "source data file", required: true, args: 1)
        cli.o(longOpt: "opusId", "UUID of the NSW Flora Opus", required: true, args: 1)
        cli.p(longOpt: "profileServiceBaseUrl", "Base URL of the profile service", required: true, args: 1)
        cli.d(longOpt: "delimiter", "Data file delimiter (defaults to ~)", required: false, args: 1)
        cli.r(longOpt: "reportFile", "File to write the results of the import to", required: false, args: 1)

        OptionAccessor opt = cli.parse(args)

        if (!opt) {
            cli.usage()
            return
        }

        String OPUS_ID = opt.o
        String DATA_FILE = opt.f
        String REPORT_FILE = opt.r ?: "report.txt"
        String PROFILE_SERVICE_IMPORT_URL = "${opt.p}/import/profile"
        String PROFILE_SERVICE_REPORT_URL = "${opt.p}/"
        String DELIMITER = opt.d ?: "~"

        List profiles = []

        println "Processing file..."
        int count = 0

        Map<String, List<Integer>> scientificNames = [:]
        Map<Integer, String> invalidLines = [:]
        String FILE_ENCODING = "UTF-8",
                DATA_DIR= "/Users/var03f/Documents/ala/profile/mangrove watch"
        String genusField = "Genus",
               speciesField = "Species Name",
               familyField = "Family",
                nameField = "Species Authority Name"


        File rpt = new File(REPORT_FILE)
        if (rpt.exists()) {
            rpt.delete()
            rpt.createNewFile()
        }

        List collectionImages = []
        List attributeNames = [
                "Plant Structure",
                "Stem Spines",
                "Stem Base",
                "Roots Exposed",
                "Milky Sap",
                "Leaf Position",
                "Leaf Form",
                "Leaflet No.",
                "Leaflet Position",
                "Leaf Shape",
                "Leaf Length",
                "Leaf Veins",
                "Leaf Margin",
                "Leaf Upper",
                "Leaf Beneath",
                "Leaf Apex",
                "Leaf Mucro",
                "Petiole Base",
                "Inflorescence Position",
                "Inflorescence Structure",
                "Inflorescence State",
                "Flower No",
                "Joints",
                "Peduncle-Pedicel L",
                "Peduncle-Pedicel L (range)",
                "Calyx Lobe Nos.",
                "Calyx Margin",
                "Calyx Surface",
                "Bracteoles",
                "Flower Shape",
                "Flower Size",
                "Petal Colour",
                "Petal No.",
                "Petal Surface Inner",
                "Petal Bristle N",
                "Petal Bristle N (range)",
                "Petal Spine",
                "Corolla Lobes",
                "Corolla Throat Surface",
                "Style L",
                "Style No.",
                "Stamen No",
                "Stamen No (list)",
                "Ovary Cells",
                "Fruit Shape",
                "Fruit Size",
                "Fruit Surface",
                "Propagule Stage",
                "Propagule Nos.",
                "Propagule Nos. (range)",
                "Propagule Nos. (list)",
                "Propagule Size",
                "Propagule Surface",
                "Radicle Surface",
                "Mangrove Or Associate",
                "Native?",
                "Common Name",
                "Synopsis Text",
                "Genus Note Text",
                "Species Note Text",
                "Species Feature Text",
                "Derivation Text",
                "Phenology Text",
                "Distribution Text",
                "Growth Form Text",
                "Foliage Text",
                "Reproductive Part Text",
                "Dispersal Text",
                "Local Distribution Text",
                "Iucn Red List Text",
                "Iucn Red List Comment"
        ]

        def csv = parseCsv(new File("${DATA_DIR}/mangrovewatch.csv").newReader(FILE_ENCODING))
        csv.each { line ->
            List attributes = []
            String scientificName = line[speciesField]?.trim()
            String nameAuthority = line[nameField]?.trim()
            attributeNames.each { propertyName ->
                try {
                    def val = line.getProperty(propertyName)?.trim()
                    if (val) {
                        attributes << [title: propertyName, text: val, stripHtml: false]
                    }
                } catch (MissingPropertyException ex) {

                }
            }

            if (!scientificName) {
                invalidLines[count] = "Unable to determine scientfic name: ${line}..."
            } else if (!scientificNames.containsKey(scientificName)) {
                Map profile = [
                        scientificName              : scientificName,
                        nameAuthor: nameAuthority,
                        nslNomenclatureMatchStrategy: "NSL_SEARCH",
                        nslNomenclatureMatchData    : [scientificName],
                        attributes                  : attributes
                ]

                profiles << profile
            }

            scientificNames.get(scientificName, []) << count
        }

        Map opus = [
                opusId  : OPUS_ID,
                profiles: profiles
        ]

        println "Importing..."
        def service = new RESTClient(PROFILE_SERVICE_IMPORT_URL)
        def resp
        try {
            resp = service.post(body: opus, requestContentType: JSON, headers: [Authorization: "Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsIm9yZy5hcGVyZW8uY2FzLnNlcnZpY2VzLlJlZ2lzdGVyZWRTZXJ2aWNlIjoiMTY1NzQzMDkwOTEwMyIsImtpZCI6ImF1dGgtdGVzdC5hbGEub3JnLmF1In0.eyJzdWIiOiJ0ZW1pLnZhcmdoZXNlQGNzaXJvLmF1Iiwicm9sZSI6WyJST0xFX0FETUlOIiwiUk9MRV9BUElfRURJVE9SIiwiUk9MRV9BUFBEX1VTRVIiLCJST0xFX0JBU0UiLCJST0xFX0NPTExFQ1RJT05fRURJVE9SIiwiUk9MRV9FRElUT1IiLCJST0xFX01EQkFfQURNSU4iLCJST0xFX1BIWUxPTElOS19BRE1JTiIsIlJPTEVfVVNFUiJdLCJvYXV0aENsaWVudElkIjoib2lkYy1leHBvLXRlc3QiLCJpc3MiOiJodHRwczpcL1wvYXV0aC10ZXN0LmFsYS5vcmcuYXVcL2Nhc1wvb2lkYyIsInByZWZlcnJlZF91c2VybmFtZSI6InRlbWkudmFyZ2hlc2VAY3Npcm8uYXUiLCJ1c2VyaWQiOiI0MjI4IiwiY2xpZW50X2lkIjoib2lkYy1leHBvLXRlc3QiLCJ1cGRhdGVkX2F0IjoiMjAxMi0xMS0xOSAwMTo0MToxMyIsImdyYW50X3R5cGUiOiJBVVRIT1JJWkFUSU9OX0NPREUiLCJzY29wZSI6WyJhbGEiLCJyb2xlcyIsInVzZXJfZGVmaW5lZCJdLCJzZXJ2ZXJJcEFkZHJlc3MiOiIxMjcuMC4wLjEiLCJsb25nVGVybUF1dGhlbnRpY2F0aW9uUmVxdWVzdFRva2VuVXNlZCI6dHJ1ZSwic3RhdGUiOiIiLCJleHAiOjE2NzEwMDEzOTYsImlhdCI6MTY3MDkxNDk5NiwianRpIjoiQVQtMTY0LUlCTFVyRmlVYmtjUDBES1VzNEROVHpWUm9HQ1FNTFByIiwiZW1haWwiOiJ0ZW1pLnZhcmdoZXNlQGNzaXJvLmF1Iiwib3JnLmFwZXJlby5jYXMuYXV0aGVudGljYXRpb24ucHJpbmNpcGFsLlJFTUVNQkVSX01FIjp0cnVlLCJjbGllbnRJcEFkZHJlc3MiOiIxNDAuMjUzLjIyOC4xNjMiLCJpc0Zyb21OZXdMb2dpbiI6ZmFsc2UsImVtYWlsX3ZlcmlmaWVkIjoiMSIsImF1dGhlbnRpY2F0aW9uRGF0ZSI6IjIwMjItMTItMDRUMjI6NTk6MDIuMTU1MzQ1WiIsInN1Y2Nlc3NmdWxBdXRoZW50aWNhdGlvbkhhbmRsZXJzIjoiUXVlcnlEYXRhYmFzZUF1dGhlbnRpY2F0aW9uSGFuZGxlciIsInVzZXJBZ2VudCI6Ik1vemlsbGFcLzUuMCAoTWFjaW50b3NoOyBJbnRlbCBNYWMgT1MgWCAxMF8xNV83KSBBcHBsZVdlYktpdFwvNTM3LjM2IChLSFRNTCwgbGlrZSBHZWNrbykgUG9zdG1hblwvOS4yOS4wIENocm9tZVwvOTQuMC40NjA2LjgxIEVsZWN0cm9uXC8xNS41LjcgU2FmYXJpXC81MzcuMzYiLCJnaXZlbl9uYW1lIjoidGVtaSIsIm5vbmNlIjoiIiwiY3JlZGVudGlhbFR5cGUiOiJSZW1lbWJlck1lVXNlcm5hbWVQYXNzd29yZENyZWRlbnRpYWwiLCJzYW1sQXV0aGVudGljYXRpb25TdGF0ZW1lbnRBdXRoTWV0aG9kIjoidXJuOm9hc2lzOm5hbWVzOnRjOlNBTUw6MS4wOmFtOnVuc3BlY2lmaWVkIiwiYXVkIjoiaHR0cDpcL1wvbG9jYWxob3N0OjMwMDAiLCJhdXRoZW50aWNhdGlvbk1ldGhvZCI6IlF1ZXJ5RGF0YWJhc2VBdXRoZW50aWNhdGlvbkhhbmRsZXIiLCJuYW1lIjoidGVtaSB2YXJnaGVzZSIsInNjb3BlcyI6WyJ1c2VyX2RlZmluZWQiLCJyb2xlcyIsImFsYSJdLCJmYW1pbHlfbmFtZSI6InZhcmdoZXNlIn0.JQ7peQv9oA0LQQOxYRf99XXuIlDHJlovVhM9cf7ovxPJ22r9VncbkBM89Z_C2wltvI5Riq2dwPG9-JlQYD-vu9ytZ0Imkp2c7s6Hyl86e82zeTpvtDDTdgitJioE-rJC5IOWNt3lA1BGFFGK21Oah5O4idQEv1Lk4CPR-VAbHAIrISUD3hZQMYc7-51EeM6RFN9fRrjYH5JQQe2niK5vNdSyNEwj_QoB4sDHmls30NQ09f1KGZNBHPB546KkWhj4pi5P015j0YnloD1FKnZgUu8K6Ptfbec6RtkLf0e2xlCgvz42AZyqnTJCFr0gOq16lnuxz1x13Pylyct1_WL3Rw"])
        } catch (groovyx.net.http.HttpResponseException ex) {
            println ex.statusCode
            println ex.response.data
            return
        }


        String importId = resp.data.id

        println "Import report will be available at ${PROFILE_SERVICE_REPORT_URL}import/${importId}/report"

        int sleepTime = 5 * 60 * 1000
        println "${new SimpleDateFormat("HH:mm:ss.S").format(new Date())} Waiting for import to complete..."
        Thread.sleep(sleepTime)

        service = new RESTClient("${PROFILE_SERVICE_REPORT_URL}import/${importId}/report")
        resp = service.get([:]).data

        while (resp.status == "IN_PROGRESS") {
            println "${new SimpleDateFormat("HH:mm:ss.S").format(new Date())} Waiting for import to complete..."
            Thread.sleep(sleepTime)

            resp = service.get([:]).data
        }

        if (invalidLines) {
            rpt << "Invalid lines from source file:\n"
            invalidLines.each { k, v ->
                rpt << "\tLine ${k}: ${v}\n"
            }
        }

        if (scientificNames.any { k, v -> v.size() > 1 && k != null }) {
            rpt << "\n\nDuplicate scientific names (only the first record will be imported): \n"
            scientificNames.each { k, v ->
                if (v.size() > 1 && k) {
                    rpt << "\t${k}, on lines ${v}. Line ${v.first()} was imported.\n"
                }
            }
        }

        int success = 0
        int failed = 0
        int warnings = 0
        rpt << "\n\nImport results: \n"
        rpt << "\nStarted: ${resp.report.started}"
        rpt << "\nFinished: ${resp.report.finished}"

        resp.report.profiles.each { k, v ->
            if (v.status.startsWith("success")) {
                success++
            } else if (v.status.startsWith("warning")) {
                warnings++
            } else {
                failed++
            }
        }

        rpt << "\n\nImported ${success} of ${count} profiles with ${failed} errors and ${warnings} warnings\n\n"

        resp.report.profiles.each { k, v ->
            if (v.status.startsWith("warning")) {
                rpt << "\t${k} succeeded with ${v.warnings.size()} warnings:\n"
                v.warnings.each {
                    rpt << "\t\t${it}\n"
                }
            } else if (v.status.startsWith("error")) {
                rpt << "\t${k} failed with ${v.errors.size()} errors and ${v.warnings.size()} warnings:\n"
                rpt << "\t\tWarnings\n"
                v.warnings.each {
                    rpt << "\t\t\t${it}\n"
                }
                rpt << "\t\tErrors\n"
                v.errors.each {
                    rpt << "\t\t\t${it}\n"
                }
            }
        }

        println "Import finished. See ${rpt.absolutePath} for details"
    }

    static String clean(String str) {
        unescapeHtml4(str)
    }

}
