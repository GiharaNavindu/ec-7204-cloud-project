variable "project_id" { type = string }
variable "region" { type = string }
variable "zone" { type = string }
variable "environment" { type = string }

variable "name_prefix" { 
  type = string 
  default = "ruhuna-auction" 
}
variable "image_tag" { 
  type = string 
  default = "bootstrap" 
}

variable "postgres_admin_username" { 
  type = string 
  default = "pgadmin" 
}
variable "postgres_password" { 
  type = string 
  sensitive = true 
}
variable "jwt_secret" { 
  type = string 
  sensitive = true 
}
variable "google_client_id" { 
  type = string 
  default = "" 
}
variable "google_client_secret" { 
  type = string 
  sensitive = true 
  default = "" 
}
variable "app_oauth2_redirect_uri" { 
  type = string 
  default = "/index.html" 
}

variable "rabbitmq_host" { type = string }
variable "rabbitmq_port" { 
  type = number 
  default = 5672 
}
variable "rabbitmq_username" { 
  type = string 
  sensitive = true 
}
variable "rabbitmq_password" { 
  type = string 
  sensitive = true 
}
variable "rabbitmq_tls" { 
  type = bool 
  default = true 
}
variable "zipkin_url" { 
  type = string 
  default = "" 
}
variable "allowed_origin_pattern" { type = string }

variable "cloud_sql_tier" { 
  type = string 
  default = "db-custom-2-7680" 
}
variable "cloud_sql_disk_gb" { 
  type = number 
  default = 50 
}
variable "redis_memory_gb" { 
  type = number 
  default = 1 
}
variable "min_instances" { 
  type = number 
  default = 1 
}
variable "max_instances" { 
  type = number 
  default = 3 
}
