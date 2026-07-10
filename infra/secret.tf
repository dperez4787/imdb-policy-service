# The Mongo URI secret is owned by the imdb-data-pipeline stack; this repo only
# grants its runtime SA read access (the service writes to its own
# imdb_governance database on the shared cluster).
data "google_secret_manager_secret" "imdb_mongodb_uri" {
  secret_id = "IMDB_MONGODB_URI"
}

resource "google_secret_manager_secret_iam_member" "runtime_reads_uri" {
  secret_id = data.google_secret_manager_secret.imdb_mongodb_uri.id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.runtime.email}"
}

# Owned by this stack. Terraform creates the containers only; the actual
# values are added as versions out-of-band (gcloud secrets versions add) so
# key material never enters Terraform state.
resource "google_secret_manager_secret" "jwt_private_key" {
  secret_id = "POLICY_JWT_PRIVATE_KEY"
  replication {
    auto {}
  }
}

resource "google_secret_manager_secret" "admin_token" {
  secret_id = "POLICY_ADMIN_TOKEN"
  replication {
    auto {}
  }
}

resource "google_secret_manager_secret_iam_member" "runtime_reads_jwt_key" {
  secret_id = google_secret_manager_secret.jwt_private_key.id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.runtime.email}"
}

resource "google_secret_manager_secret_iam_member" "runtime_reads_admin_token" {
  secret_id = google_secret_manager_secret.admin_token.id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.runtime.email}"
}
