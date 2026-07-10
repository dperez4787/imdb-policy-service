resource "google_artifact_registry_repository" "policy_service" {
  repository_id = "imdb-policy-service"
  location      = local.region
  format        = "DOCKER"
  description   = "imdb-policy-service images (governance system of record)"

  cleanup_policies {
    id     = "keep-recent"
    action = "KEEP"
    most_recent_versions {
      keep_count = 10
    }
  }

  cleanup_policies {
    id     = "delete-old"
    action = "DELETE"
    condition {
      older_than = "2592000s" # 30 days
    }
  }
}
