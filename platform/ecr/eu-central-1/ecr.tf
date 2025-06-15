locals {
  frever-repos = ["appservice", "cms", "frever-web", "load-test-controller", "load-test-worker", "cms-authorization", "timers", "ixia-web"]
}

resource "aws_ecr_repository" "frever-ecr" {
  for_each             = toset(local.frever-repos)
  name                 = each.key
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_lifecycle_policy" "lifecycle-policy" {
  depends_on = [aws_ecr_repository.frever-ecr]
  for_each   = toset(local.frever-repos)
  repository = each.key
  policy     = file("lifecycle-policy.json")
}

