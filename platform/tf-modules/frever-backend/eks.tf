data "aws_caller_identity" "current" {}

data "aws_iam_policy_document" "eks-cluster-assume-role" {
  statement {
    effect = "Allow"

    principals {
      type        = "Service"
      identifiers = ["eks.amazonaws.com"]
    }

    actions = ["sts:AssumeRole"]
  }
}

resource "aws_iam_role" "eks-cluster" {
  name               = "${var.vpc_name}-eks-cluster"
  assume_role_policy = data.aws_iam_policy_document.eks-cluster-assume-role.json
}

resource "aws_iam_role_policy_attachment" "AmazonEKSClusterPolicy" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSClusterPolicy"
  role       = aws_iam_role.eks-cluster.name
}

# Optionally, enable Security Groups for Pods
# Reference: https://docs.aws.amazon.com/eks/latest/userguide/security-groups-for-pods.html
resource "aws_iam_role_policy_attachment" "AmazonEKSVPCResourceController" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSVPCResourceController"
  role       = aws_iam_role.eks-cluster.name
}

resource "aws_security_group" "eks-cluster-sg" {
  name        = "${var.vpc_name}-eks-cluster-sg"
  description = "${var.vpc_name} EKS cluster sg"
  vpc_id      = aws_vpc.vpc.id
  tags = {
    Name = "${var.vpc_name}-eks-cluster-sg"
  }
}

resource "aws_security_group_rule" "eks-cluster-sg-in-from-itself" {
  security_group_id        = aws_security_group.eks-cluster-sg.id
  type                     = "ingress"
  from_port                = 0
  to_port                  = 65536
  protocol                 = "-1"
  source_security_group_id = aws_security_group.eks-cluster-sg.id
}

resource "aws_security_group_rule" "eks-cluster-sg-in-vpc" {
  security_group_id = aws_security_group.eks-cluster-sg.id
  type              = "ingress"
  from_port         = 0
  to_port           = 65536
  protocol          = "-1"
  cidr_blocks       = [aws_vpc.vpc.cidr_block]
}

resource "aws_security_group_rule" "eks-cluster-sg-out-vpc" {
  security_group_id = aws_security_group.eks-cluster-sg.id
  type              = "egress"
  from_port         = 0
  to_port           = 65536
  protocol          = "-1"
  cidr_blocks       = [aws_vpc.vpc.cidr_block]
}

resource "aws_security_group_rule" "eks-cluster-sg-out-https" {
  security_group_id = aws_security_group.eks-cluster-sg.id
  type              = "egress"
  from_port         = 443
  to_port           = 443
  protocol          = "tcp"
  cidr_blocks       = ["0.0.0.0/0"]
  ipv6_cidr_blocks  = ["::/0"]
}

resource "aws_security_group_rule" "eks-cluster-sg-out-k8s" {
  security_group_id        = aws_security_group.eks-cluster-sg.id
  type                     = "egress"
  from_port                = 10520
  to_port                  = 10520
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.eks-cluster-sg.id
}

resource "aws_security_group_rule" "eks-cluster-sg-out-dns-tcp" {
  security_group_id        = aws_security_group.eks-cluster-sg.id
  type                     = "egress"
  from_port                = 53
  to_port                  = 53
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.eks-cluster-sg.id
}

resource "aws_security_group_rule" "eks-cluster-sg-out-dns-udp" {
  security_group_id        = aws_security_group.eks-cluster-sg.id
  type                     = "egress"
  from_port                = 53
  to_port                  = 53
  protocol                 = "udp"
  source_security_group_id = aws_security_group.eks-cluster-sg.id
}

resource "aws_eks_cluster" "eks-cluster" {
  name     = "${var.vpc_name}-eks-cluster"
  role_arn = aws_iam_role.eks-cluster.arn
  version  = "1.32"

  vpc_config {
    subnet_ids              = aws_subnet.private[*].id
    security_group_ids      = [aws_security_group.eks-cluster-sg.id]
    endpoint_private_access = true
  }

  # Ensure that IAM Role permissions are created before and deleted after EKS Cluster handling.
  # Otherwise, EKS will not be able to properly delete EKS managed EC2 infrastructure such as Security Groups.
  depends_on = [
    aws_iam_role_policy_attachment.AmazonEKSClusterPolicy,
    aws_iam_role_policy_attachment.AmazonEKSVPCResourceController,
  ]
}

output "endpoint" {
  value = aws_eks_cluster.eks-cluster.endpoint
}

output "kubeconfig-certificate-authority-data" {
  value = aws_eks_cluster.eks-cluster.certificate_authority[0].data
}

data "aws_ssm_parameter" "eks_ami_release_version" {
  name = "/aws/service/eks/optimized-ami/${aws_eks_cluster.eks-cluster.version}/amazon-linux-2/recommended/release_version"
}

resource "aws_iam_role" "eks-node-group" {
  name = "${var.vpc_name}-eks-node-group"

  assume_role_policy = jsonencode({
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "ec2.amazonaws.com"
      }
      },
      {
        Action = "sts:AssumeRoleWithWebIdentity"
        Sid    = "AppServiceRoleAssume"
        Effect = "Allow"
        Principal = {
          Federated = aws_iam_openid_connect_provider.eks-oidc.arn
        }
        Condition = {
          StringEquals = {
            "${aws_iam_openid_connect_provider.eks-oidc.url}:aud" = "sts.amazonaws.com"
          }
          StringLike = {
            "${aws_iam_openid_connect_provider.eks-oidc.url}:sub" = ["system:serviceaccount:${aws_eks_cluster.eks-cluster.name}*:default", "system:serviceaccount:app*:default"]
          }
        }
      }
    ]
    Version = "2012-10-17"
  })
}

resource "aws_iam_role_policy" "eks-management-policy" {
  name = "${var.vpc_name}-eks-cluster-management-policy"
  role = aws_iam_role.eks-node-group.name

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = [
          "iam:CreateServiceLinkedRole",
          "ec2:Describe*",
          "ec2:*Tags",
          "ec2:*SecurityGroup",
          "ec2:*SecurityGroupIngress",
          "ec2:Get*",
          "elasticloadbalancing:SetWebAcl",
          "elasticloadbalancing:Describe*",
          "elasticloadbalancing:*Tags",
          "elasticloadbalancing:*TargetGroup",
          "elasticloadbalancing:*Targets",
          "elasticloadbalancing:*ListenerCertificates",
          "elasticloadbalancing:Modify*Attributes",
          "elasticloadbalancing:*Rule",
          "elasticloadbalancing:*LoadBalancer",
          "elasticloadbalancing:*Listener",
        ]
        Sid      = "EksManageLB"
        Effect   = "Allow"
        Resource = "*"
      },
      {
        Action = [
          "autoscaling:Describe*",
          "autoscaling:SetDesiredCapacity",
          "autoscaling:TerminateInstanceInAutoScalingGroup",
          "eks:Describe*"
        ]
        Sid      = "EksManageAutoScaling"
        Effect   = "Allow"
        Resource = "*"
      },
      {
        Action = [
          "cognito-idp:DescribeUserPoolClient",
          "acm:ListCertificates",
          "acm:DescribeCertificate",
          "iam:ListServerCertificates",
          "iam:GetServerCertificate",
          "waf-regional:GetWebACL",
          "waf-regional:GetWebACLForResource",
          "waf-regional:AssociateWebACL",
          "waf-regional:DisassociateWebACL",
          "wafv2:GetWebACL",
          "wafv2:GetWebACLForResource",
          "wafv2:AssociateWebACL",
          "wafv2:DisassociateWebACL",
          "shield:GetSubscriptionState",
          "shield:DescribeProtection",
          "shield:CreateProtection",
          "shield:DeleteProtection"
        ]
        Sid      = "EksManageSecurityRelated"
        Effect   = "Allow"
        Resource = "*"
      },
      {
        Action = [
          "route53:List*",
          "route53:ChangeResourceRecordSets"
        ]
        Sid      = "EksManageDNS"
        Effect   = "Allow"
        Resource = "*"
      },
    ]
  })
}

resource "aws_iam_role_policy" "app-policy" {
  name = "${var.vpc_name}-eks-cluster-app-policy"
  role = aws_iam_role.eks-node-group.name

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action   = "ssm:DescribeParameters"
        Sid      = "DescribeParameters"
        Effect   = "Allow"
        Resource = "*"
      },
      {
        Action = [
          "ssm:PutParameter",
          "ssm:DeleteParameter",
          "ssm:GetParameterHistory",
          "ssm:GetParametersByPath",
          "ssm:GetParameters",
          "ssm:GetParameter",
          "ssm:DeleteParameters"
        ]
        Sid      = "UseSsmParameters"
        Effect   = "Allow"
        Resource = "arn:aws:ssm:*:${data.aws_caller_identity.current.account_id}:parameter/${var.vpc_name}/*"
      },
      {
        Action   = "kms:Decrypt"
        Sid      = "DecryptSsmParameters"
        Effect   = "Allow"
        Resource = data.aws_kms_key.ssm.arn
      },
      {
        Action   = ["mediaconvert:CreateJob", "mediaconvert:List*", "mediaconvert:Get*", "mediaconvert:Describe*"]
        Sid      = "MediaConvertPermissions"
        Effect   = "Allow"
        Resource = "*"
      },
      {
        Action   = ["elastictranscoder:Read*", "elastictranscoder:List*", "elastictranscoder:*Job", "elastictranscoder:*Preset", "elastictranscoder:*Pipeline*"]
        Sid      = "ElasticTranscoderPermissions"
        Effect   = "Allow"
        Resource = "*"
      },
      {
        Action   = ["s3:List*", "s3:DeleteObject*", "s3:Get*", "s3:Put*Object*", "s3:RestoreObject"]
        Sid      = "S3Permissions"
        Effect   = "Allow"
        Resource = "*"
      },
      {
        Action   = ["ses:SendEmail", "ses:SendRawEmail"]
        Sid      = "SesPermissions"
        Effect   = "Allow"
        Resource = "*"
      },
      {
        Action   = ["sqs:Change*", "sqs:DeleteMessage*", "sqs:Get*", "sqs:List*", "sqs:ReceiveMessage", "sqs:Send*", "sqs:SetQueueAttributes"]
        Sid      = "SqsPermissions"
        Effect   = "Allow"
        Resource = "*"
      },
      {
        Action   = ["sns:Publish*"]
        Sid      = "SnsPermissions"
        Effect   = "Allow"
        Resource = "arn:aws:sns:*:${data.aws_caller_identity.current.account_id}:${trimprefix(var.vpc_name, "content-")}-*"
      },
      {
        Action   = ["sagemaker:InvokeEndpoint*"]
        Sid      = "SageMakerPermissions"
        Effect   = "Allow"
        Resource = "*"
      },
      {
        Action   = ["iam:PassRole"]
        Sid      = "AllowPassRoles"
        Effect   = "Allow"
        Resource = ["arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/*media-convert*role"]
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "AmazonEKSWorkerNodePolicy" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSWorkerNodePolicy"
  role       = aws_iam_role.eks-node-group.name
}

resource "aws_iam_role_policy_attachment" "AmazonEKS_CNI_Policy" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKS_CNI_Policy"
  role       = aws_iam_role.eks-node-group.name
}

resource "aws_iam_role_policy_attachment" "AmazonEC2ContainerRegistryReadOnly" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
  role       = aws_iam_role.eks-node-group.name
}

data "aws_security_group" "eks-node-group-sg" {
  depends_on = [
    aws_eks_node_group.eks-node-group,
  ]
  name = "eks-cluster-sg-${aws_eks_cluster.eks-cluster.name}-*"
}

resource "aws_eks_node_group" "eks-node-group" {
  cluster_name    = aws_eks_cluster.eks-cluster.name
  node_group_name = "${var.vpc_name}-apps"
  version         = aws_eks_cluster.eks-cluster.version
  release_version = nonsensitive(data.aws_ssm_parameter.eks_ami_release_version.value)
  node_role_arn   = aws_iam_role.eks-node-group.arn
  subnet_ids      = aws_subnet.private[*].id
  instance_types  = var.eks_node_group_instance_types
  labels = {
    Environment = var.vpc_name
    AppGroup    = "frever"
  }

  scaling_config {
    desired_size = var.eks_node_group_desired_size
    max_size     = var.eks_node_group_max_size
    min_size     = var.eks_node_group_min_size
  }

  update_config {
    max_unavailable = 1
  }

  remote_access {
    ec2_ssh_key               = data.aws_key_pair.platform.key_name
    source_security_group_ids = [aws_security_group.bastion-sg.id]
  }

  lifecycle {
    ignore_changes = [scaling_config[0].desired_size]
  }

  # Ensure that IAM Role permissions are created before and deleted after EKS Node Group handling.
  # Otherwise, EKS will not be able to properly delete EC2 Instances and Elastic Network Interfaces.
  depends_on = [
    aws_iam_role_policy_attachment.AmazonEKSWorkerNodePolicy,
    aws_iam_role_policy_attachment.AmazonEKS_CNI_Policy,
    aws_iam_role_policy_attachment.AmazonEC2ContainerRegistryReadOnly,
  ]
}

resource "aws_eks_addon" "vpc-cni" {
  cluster_name                = aws_eks_cluster.eks-cluster.name
  addon_name                  = "vpc-cni"
  resolve_conflicts_on_update = "PRESERVE"
}

resource "aws_eks_addon" "coredns" {
  cluster_name                = aws_eks_cluster.eks-cluster.name
  addon_name                  = "coredns"
  resolve_conflicts_on_update = "PRESERVE"
}

resource "aws_eks_addon" "kube-proxy" {
  cluster_name                = aws_eks_cluster.eks-cluster.name
  addon_name                  = "kube-proxy"
  resolve_conflicts_on_update = "PRESERVE"
}

data "tls_certificate" "eks-certificate" {
  url = aws_eks_cluster.eks-cluster.identity[0].oidc[0].issuer
}

resource "aws_iam_openid_connect_provider" "eks-oidc" {
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = [data.tls_certificate.eks-certificate.certificates[0].sha1_fingerprint]
  url             = aws_eks_cluster.eks-cluster.identity[0].oidc[0].issuer
}

resource "aws_eks_addon" "aws-ebs-csi-driver" {
  cluster_name                = aws_eks_cluster.eks-cluster.name
  addon_name                  = "aws-ebs-csi-driver"
  resolve_conflicts_on_update = "PRESERVE"
  service_account_role_arn    = aws_iam_role.aws-eks-csi-driver-role.arn
}

data "aws_iam_policy" "AmazonEBSCSIDriverPolicy" {
  name = "AmazonEBSCSIDriverPolicy"
}

resource "aws_iam_role" "aws-eks-csi-driver-role" {
  name = "${var.vpc_name}-cluster-aws-eks-csi-driver-role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRoleWithWebIdentity"
        Sid    = "EbsCsiDriverAssume"
        Effect = "Allow"
        Principal = {
          Federated = aws_iam_openid_connect_provider.eks-oidc.arn
        }
        Condition = {
          StringEquals = {
            "${aws_iam_openid_connect_provider.eks-oidc.url}:aud" = "sts.amazonaws.com"
          }
          StringLike = {
            "${aws_iam_openid_connect_provider.eks-oidc.url}:sub" = "system:serviceaccount:kube-system:ebs-csi-controller-sa"
          }
        }
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "test-attach" {
  role       = aws_iam_role.aws-eks-csi-driver-role.name
  policy_arn = data.aws_iam_policy.AmazonEBSCSIDriverPolicy.arn
}

data "aws_kms_key" "ssm" {
  key_id = "alias/aws/ssm"
}

