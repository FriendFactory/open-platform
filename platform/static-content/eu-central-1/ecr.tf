resource "aws_ecr_repository" "static-content" {
  name                 = "static-content-nginx"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_lifecycle_policy" "static-content-lifecycle-policy" {
  depends_on = [aws_ecr_repository.static-content]
  repository = aws_ecr_repository.static-content.name
  policy     = file("lifecycle-policy.json")
}

