
final String shouldPublishFailJobStatusName = 'SHOULD_PUBLISH_FAIL_JOB_STATUS'

pipeline {
	parameters {
		string name: 'JOB_BRANCH_NAME', description: 'branch to trigger jobs', defaultValue: 'dev'
		booleanParam name: shouldPublishFailJobStatusName, description: 'true to publish job status if failed', defaultValue: true
		booleanParam name: 'WAIT_FOR_BUILDS', description: 'true to wait for trigger build to complete', defaultValue: true
	}

	agent {
		label 'ubuntu-agent'
	}

	triggers {
		cron('@midnight')
	}

	options {
		ansiColor('css')
		timestamps()
	}

	stages {
		stage('display environment') {
			steps {
				sh 'printenv'
			}
		}
		stage('trigger build jobs') {
			steps {
				script {
					triggerAllJobs(env.JOB_BRANCH_NAME, env.WAIT_FOR_BUILDS ? env.WAIT_FOR_BUILDS.toBoolean() : true)
				}
			}
		}
	}
	post {
		unsuccessful {
			script {
				if (null != env.SHOULD_PUBLISH_FAIL_JOB_STATUS && env.SHOULD_PUBLISH_FAIL_JOB_STATUS.toBoolean()) {
					helper.sendDiscordNotification(
						"Jenkins Job Failed for ${currentBuild.fullDisplayName}",
						"At least one job failed for Build#${env.BUILD_NUMBER} which has a result of ${currentBuild.currentResult}.",
						env.BUILD_URL,
						currentBuild.currentResult
					)
				}
			}
		}
	}
}

void triggerAllJobs(String branchName, boolean waitForDownStream) {
	Map<String, String> siblingNameMap = siblingJobNames()
	Map<String, Closure> buildJobs = [:]
	final String platformName = 'PLATFORM'

	siblingNameMap.each { siblingName ->
		String displayName = siblingName.value
		buildJobs["${displayName}"] = {
			stage("${displayName}") {
				String fullJobName = siblingName.key + '/' + branchName

				// Platform parameter can vary per project, get the last value
				String platform = lastJobParameterValue(fullJobName, platformName)
				ehco "param - ${shouldPublishFailJobStatusName}"
				build job: "${fullJobName}", parameters: [
					gitParameter(name: 'MANUAL_GIT_BRANCH', value: branchName),
					string(name: platformName, value: platform ?: 'ubuntu'),
					string(name: 'BUILD_CONFIGURATION', value: 'release-private'),
					string(name: 'TEST_MODE', value: 'code-coverage'),
					booleanParam(name: 'SHOULD_PUBLISH_IMAGE', value: false),
					booleanParam(name: shouldPublishFailJobStatusName, value: env.SHOULD_PUBLISH_FAIL_JOB_STATUS.toBoolean())],
					wait: waitForDownStream
			}
		}
	}

	parallel buildJobs
}

Map<String, String> siblingJobNames() {
	Item project = Jenkins.get().getItemByFullName(currentBuild.fullProjectName)
	List<Items> siblingItems = project.parent.items

	Map<String, String> targets = [:]
	for (item in siblingItems) {
		if (!(item instanceof AbstractModelObject) || item.fullName == project.fullName) {
			continue
		}

		targets.put(item.fullName, item.displayName)
	}

	return targets
}

Object lastJobParameterValue(String fullJobName, String parameterName) {
	Item job = Jenkins.get().getItemByFullName(fullJobName)
	ParametersAction params = job.getLastBuild().getAction(hudson.model.ParametersAction)
	return params.getParameter(parameterName).getValue()
}
