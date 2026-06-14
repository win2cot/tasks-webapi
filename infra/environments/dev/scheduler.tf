# ---------------------------------------------------------------------------
# tasks dev スケジューラ — tasks-dev-webapi (ECS) + tasks-dev-mysql (RDS) のみ制御
# platform(keycloak) は infra/shared/environments/dev が独立スケジューラで制御 (#625)
#
# 二段スケジュール(fire-and-forget / 待機なし):
#   RDS start 18:45(平日) / 09:45(土日) — ECS 起動の 15 分前に先行発火
#   ECS start 19:00(平日) / 10:00(土日) — RDS 暖機済み前提で起動
#   stop      毎日 02:00             — ECS desiredCount=0 → RDS stop
# ---------------------------------------------------------------------------

module "scheduler" {
  source = "../../modules/scheduler"

  env        = var.env
  stack      = "tasks"
  region     = var.region
  account_id = data.aws_caller_identity.current.account_id

  ecs_service_arns = [
    "arn:aws:ecs:${var.region}:${data.aws_caller_identity.current.account_id}:service/tasks-${var.env}-cluster/tasks-${var.env}-webapi",
  ]
  rds_instance_arns = [
    "arn:aws:rds:${var.region}:${data.aws_caller_identity.current.account_id}:db:${module.rds.db_instance_identifier}",
  ]

  schedules = [
    # 平日 18:45 JST — RDS start (ECS の 15 分前)
    {
      name                = "rds-start-weekday"
      schedule_expression = "cron(45 18 ? * MON-FRI *)"
      input = jsonencode({
        action = "start"
        rds    = ["tasks-${var.env}-mysql"]
        ecs    = []
      })
    },
    # 土日 09:45 JST — RDS start (ECS の 15 分前)
    {
      name                = "rds-start-weekend"
      schedule_expression = "cron(45 9 ? * SAT-SUN *)"
      input = jsonencode({
        action = "start"
        rds    = ["tasks-${var.env}-mysql"]
        ecs    = []
      })
    },
    # 平日 19:00 JST — ECS start
    {
      name                = "ecs-start-weekday"
      schedule_expression = "cron(0 19 ? * MON-FRI *)"
      input = jsonencode({
        action = "start"
        rds    = []
        ecs    = [["tasks-${var.env}-cluster", "tasks-${var.env}-webapi"]]
      })
    },
    # 土日 10:00 JST — ECS start
    {
      name                = "ecs-start-weekend"
      schedule_expression = "cron(0 10 ? * SAT-SUN *)"
      input = jsonencode({
        action = "start"
        rds    = []
        ecs    = [["tasks-${var.env}-cluster", "tasks-${var.env}-webapi"]]
      })
    },
    # 毎日 02:00 JST — ECS desiredCount=0 + RDS stop
    {
      name                = "stop-daily"
      schedule_expression = "cron(0 2 * * ? *)"
      input = jsonencode({
        action = "stop"
        rds    = ["tasks-${var.env}-mysql"]
        ecs    = [["tasks-${var.env}-cluster", "tasks-${var.env}-webapi"]]
      })
    },
  ]
}
