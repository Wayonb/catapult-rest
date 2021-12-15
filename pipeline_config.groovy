jte {
    pipeline_template = "Jenkinsfile.test"
}

/*
  define libraries to load.
  available libraries are based upon the
  library sources configured.
*/
libraries{
    metal_build{}
    @override build_setup {
        testScriptName = ["unit_test.sh catapult-sdk", "unit_test.sh rest", "unit_test.sh spammer"]
    }
    publish_artifacts {
        docker {
	  image_name = 'symbolplatform/symbol-server-private'
        }
    }
}
