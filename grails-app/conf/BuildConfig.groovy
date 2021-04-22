grails.servlet.version = "3.0" // Change depending on target container compliance (2.5 or 3.0)
grails.project.class.dir = "target/classes"
grails.project.test.class.dir = "target/test-classes"
grails.project.test.reports.dir = "target/test-reports"
grails.project.work.dir = "target/work"
grails.project.target.level = 1.7
grails.project.source.level = 1.7
grails.reload.enabled = true
//grails.project.war.file = "target/${appName}-${appVersion}.war"

grails.project.fork = [
    // configure settings for compilation JVM, note that if you alter the Groovy version forked compilation is required
    //  compile: [maxMemory: 256, minMemory: 64, debug: false, maxPerm: 256, daemon:true],

    // configure settings for the test-app JVM, uses the daemon by default
    test: [maxMemory: 768, minMemory: 64, debug: false, maxPerm: 256, daemon:true],
    // configure settings for the run-app JVM
    run: [maxMemory: 768, minMemory: 64, debug: false, maxPerm: 256, forkReserve:false],
    // configure settings for the run-war JVM
    war: [maxMemory: 768, minMemory: 64, debug: false, maxPerm: 256, forkReserve:false],
    // configure settings for the Console UI JVM
    console: [maxMemory: 768, minMemory: 64, debug: false, maxPerm: 256]
]

grails.project.dependency.resolver = "maven" // or ivy
grails.project.dependency.resolution = {
    // inherit Grails' default dependencies
    inherits("global") {
        // specify dependency exclusions here; for example, uncomment this to disable ehcache:
        // excludes 'ehcache'
    }
    log "error" // log level of Ivy resolver, either 'error', 'warn', 'info', 'debug' or 'verbose'
    checksums true // Whether to verify checksums on resolve
    legacyResolve false // whether to do a secondary resolve on plugin installation, not advised and here for backwards compatibility

    repositories {
        mavenLocal()
        mavenRepo "http://nexus.ala.org.au/content/repositories/grails-plugins/"
        mavenRepo ("http://nexus.ala.org.au/content/groups/public/") {
            updatePolicy 'always'
        }
    }

    dependencies {
        // specify dependencies here under either 'build', 'compile', 'runtime', 'test' or 'provided' scopes e.g.
        test 'org.grails:grails-datastore-test-support:1.0.1-grails-2.4'
        runtime ('au.org.ala:ala-name-matching:2.4.1') {
            excludes 'lucene-queries', 'lucene-analyzers', 'lucene-core', 'lucene-analyzers-common', 'lucene-queryparser', 'lucene-sandbox', 'slf4j-log4j12'
        }
        compile "com.xlson.groovycsv:groovycsv:1.0"
        compile "com.google.guava:guava:14.0.1"
        compile ("com.google.apis:google-api-services-analytics:v3-rev116-1.20.0") {
            excludes 'guava-jdk5'
        }
        compile 'com.googlecode.owasp-java-html-sanitizer:owasp-java-html-sanitizer:20160827.1'
        compile "com.itextpdf:itextpdf:5.5.1"
        compile "org.imgscalr:imgscalr-lib:4.2"
        // use an updated elasticsearch library that matches server major.minor version
        compile 'org.elasticsearch:elasticsearch-groovy:1.7.4', {
            excludes 'groovy-all'
        }
    }

    plugins {
        build ":release:3.0.1"
        build ":tomcat:7.0.70"
        runtime ":ala-auth:2.1.6"
        runtime ":ala-ws-security:1.4"
        runtime ":ala-ws-plugin:1.6.1"
        runtime ":mongodb:3.0.3"
        compile (":elasticsearch:0.0.4.6") {
            excludes 'groovy-all', 'elasticsearch-groovy' // elasticsearch:0.0.4.6 bundles groovy-all:2.4.3; Grails 2.5.5 uses groovy-all:2.4.4.  We provide our own ES client
        }
        runtime ":cors:1.1.6"
        compile ":asset-pipeline:2.9.1"
        compile ":quartz:1.0.2"
        compile ":mail:1.0.7"
        compile ":yammer-metrics:3.0.1-2"
        compile "org.grails.plugins:build-info:1.2.8"
    }
}
