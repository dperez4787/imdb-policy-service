output "wif_provider" {
  description = "Value for the WIF_PROVIDER GitHub secret"
  value       = google_iam_workload_identity_pool_provider.github_policysvc.name
}

output "deploy_sa" {
  description = "Value for the DEPLOY_SA GitHub secret"
  value       = google_service_account.deploy.email
}

output "runtime_sa" {
  description = "Runtime identity of the Cloud Run service"
  value       = google_service_account.runtime.email
}
