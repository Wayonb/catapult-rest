jte {
   @override pipeline_template = "Jenkinsfile.test"
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
        testScriptName = ["unit_test.sh catapult-sdk", "unit_test.sh rest", "unit_test.sh spammer"]
        registry = "https://registry.hub.docker.com" 
        credential_id = "docker-hub-token-symbolserverbot"
    }
    publish_artifacts {
        docker {
	  image_name = 'symbolplatform/symbol-server-private'
        }
    }
}
