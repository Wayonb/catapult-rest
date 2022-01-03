jte {
   @override pipeline_template = "Jenkinsfile_mental"
}

/*
  define libraries to load.
  available libraries are based upon the
  library sources configured.
*/
libraries{
    docker_build {
	environment = 'nodejs'
    }
    @override build_setup {
        test_script_name = ["unit_test.sh catapult-sdk", "unit_test.sh rest", "unit_test.sh spammer"]
        build_setup = "setup_build.sh"
        setup_tests = "setup_tests.sh"
    }
    publish_artifacts {
        docker {
	  image_name = 'symbolplatform/symbol-server-private'
        }
    }
}
