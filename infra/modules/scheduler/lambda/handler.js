// ECS + RDS Scheduler — Node.js 24.x / AWS SDK v3 (runtime-bundled)
// Event payload: { action: "start"|"stop", rds: [...ids], ecs: [[cluster, service], ...] }
// Fire-and-forget: no polling or wait loops. Per-target try/catch prevents one failure blocking others.

const { ECSClient, UpdateServiceCommand } = require("@aws-sdk/client-ecs");
const { RDSClient, StartDBInstanceCommand, StopDBInstanceCommand } = require("@aws-sdk/client-rds");

const ecs = new ECSClient({});
const rds = new RDSClient({});

exports.handler = async (event) => {
  const action = event.action;
  const rdsIds = event.rds ?? [];
  const ecsTargets = event.ecs ?? [];
  const results = [];

  if (action === "start") {
    for (const instanceId of rdsIds) {
      results.push(await rdsOp("start", instanceId));
    }
    for (const [cluster, service] of ecsTargets) {
      results.push(await ecsOp("start", cluster, service));
    }
  } else if (action === "stop") {
    for (const [cluster, service] of ecsTargets) {
      results.push(await ecsOp("stop", cluster, service));
    }
    for (const instanceId of rdsIds) {
      results.push(await rdsOp("stop", instanceId));
    }
  } else {
    throw new Error(`Unknown action: ${action}`);
  }

  console.log(JSON.stringify({ level: "INFO", action, results }));
  return { action, results };
};

async function rdsOp(action, instanceId) {
  try {
    if (action === "start") {
      await rds.send(new StartDBInstanceCommand({ DBInstanceIdentifier: instanceId }));
    } else {
      await rds.send(new StopDBInstanceCommand({ DBInstanceIdentifier: instanceId }));
    }
    const entry = { level: "INFO", type: "rds", action, id: instanceId, status: "issued" };
    console.log(JSON.stringify(entry));
    return entry;
  } catch (e) {
    if (e.name === "InvalidDBInstanceStateFault") {
      const entry = { level: "INFO", type: "rds", action, id: instanceId, status: "skipped", reason: "invalid_state" };
      console.log(JSON.stringify(entry));
      return entry;
    }
    const entry = { level: "ERROR", type: "rds", action, id: instanceId, status: "error", error: e.message };
    console.log(JSON.stringify(entry));
    return entry;
  }
}

async function ecsOp(action, cluster, service) {
  const desiredCount = action === "start" ? 1 : 0;
  try {
    await ecs.send(new UpdateServiceCommand({ cluster, service, desiredCount }));
    const entry = { level: "INFO", type: "ecs", action, id: `${cluster}/${service}`, status: "issued", desiredCount };
    console.log(JSON.stringify(entry));
    return entry;
  } catch (e) {
    const entry = { level: "ERROR", type: "ecs", action, id: `${cluster}/${service}`, status: "error", error: e.message };
    console.log(JSON.stringify(entry));
    return entry;
  }
}
