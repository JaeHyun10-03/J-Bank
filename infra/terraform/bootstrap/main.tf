# 인프라아키텍처 문서 10절: 상태 파일은 S3, 잠금은 DynamoDB. envs/dev·envs/prod가
# 이 스택의 출력값(state_bucket, lock_table)을 backend 설정에 그대로 넣어 쓴다.
# 딱 한 번(또는 아주 가끔) apply하는 스택이라 다른 모듈들과 달리 dev/prod 구분 없이
# 계정당 하나만 둔다.

data "aws_caller_identity" "current" {}

resource "aws_s3_bucket" "state" {
  bucket = "jbank-terraform-state-${data.aws_caller_identity.current.account_id}"

  tags = { Project = "j-bank", ManagedBy = "terraform", Purpose = "terraform-state" }
}

resource "aws_s3_bucket_versioning" "state" {
  bucket = aws_s3_bucket.state.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "state" {
  bucket = aws_s3_bucket.state.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "state" {
  bucket = aws_s3_bucket.state.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_dynamodb_table" "lock" {
  name         = "jbank-terraform-locks"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "LockID"

  attribute {
    name = "LockID"
    type = "S"
  }

  tags = { Project = "j-bank", ManagedBy = "terraform", Purpose = "terraform-state-lock" }
}
