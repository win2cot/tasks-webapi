import os
import time

import boto3
from botocore.exceptions import ClientError


def handler(event, context):
    action = event["action"]  # "start" or "stop"
    ecs = boto3.client("ecs")
    rds = boto3.client("rds")
    ecs_targets = [t.split("/") for t in os.environ["ECS_TARGETS"].split(",")]
    rds_id = os.environ["RDS_INSTANCE_ID"]

    if action == "start":
        # 起動順序: RDS available 後に ECS 起動(DB 接続エラーループ回避)
        _rds_start(rds, rds_id)
        _rds_wait_available(rds, rds_id)
        for cluster, service in ecs_targets:
            ecs.update_service(cluster=cluster, service=service, desiredCount=1)
    elif action == "stop":
        # 停止順序: ECS を先に 0 にしてから RDS を停止(接続が残らない状態で停止)
        for cluster, service in ecs_targets:
            ecs.update_service(cluster=cluster, service=service, desiredCount=0)
        _rds_stop(rds, rds_id)
    else:
        raise ValueError(f"Unknown action: {action}")

    return {"action": action, "rds_instance_id": rds_id, "ecs_targets": len(ecs_targets)}


def _rds_start(rds, instance_id):
    try:
        rds.start_db_instance(DBInstanceIdentifier=instance_id)
    except ClientError as e:
        if e.response["Error"]["Code"] == "InvalidDBInstanceState":
            pass  # already running or in transition; proceed to wait
        else:
            raise


def _rds_stop(rds, instance_id):
    try:
        rds.stop_db_instance(DBInstanceIdentifier=instance_id)
    except ClientError as e:
        if e.response["Error"]["Code"] == "InvalidDBInstanceState":
            pass  # already stopped or stopping
        else:
            raise


def _rds_wait_available(rds, instance_id, timeout=240, interval=15):
    """Poll until RDS status is 'available'. Raises TimeoutError if not ready in time."""
    elapsed = 0
    status = "unknown"
    while elapsed < timeout:
        resp = rds.describe_db_instances(DBInstanceIdentifier=instance_id)
        status = resp["DBInstances"][0]["DBInstanceStatus"]
        if status == "available":
            return
        time.sleep(interval)
        elapsed += interval
    raise TimeoutError(f"RDS {instance_id} not available after {timeout}s (last status: {status})")
