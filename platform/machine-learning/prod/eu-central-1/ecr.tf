locals {
  machine-learning-repos = ["template-recsys", "template-recsys-pipeline", "template-inference", "template-recsys-dataset-generation", "template-recsys-model-training", "who-to-follow", "who-to-follow-inference", "creator-business", "creator-inference", "video-gen", "crew", "feed-recsys", "ml-service"]
}

resource "aws_ecr_repository" "machine-learning-ecr" {
  for_each             = toset(local.machine-learning-repos)
  name                 = each.key
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_lifecycle_policy" "lifecycle-policy" {
  depends_on = [aws_ecr_repository.machine-learning-ecr]
  for_each   = toset(local.machine-learning-repos)
  repository = each.key
  policy     = file("lifecycle-policy.json")
}

