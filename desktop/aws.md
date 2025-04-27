### Current Userdata

```shell
#!/bin/bash
# Configuration
APP_DIR="/opt/cognotik"
APP_USER="ec2-user"
S3_BUCKET="share.simiacrypt.us"
DB_URL="jdbc:postgresql://apps-simiacrypt-us.cluster-ckhbwauobdwe.us-east-1.rds.amazonaws.com:5432/"
LOG_FILE="/var/log/appserver.log"
# Enable logging
exec 1> >(tee -a "/var/log/userdata.log")
exec 2>&1
echo "[$(date)] Starting application deployment..."

# Create application directory
echo "[$(date)] Creating application directory..."
sudo mkdir -p ${APP_DIR}
sudo chown ${APP_USER}:${APP_USER} ${APP_DIR}

# Copy application files from S3
echo "[$(date)] Copying application files from S3..."
aws s3 cp -R s3://${S3_BUCKET}/libs/ ${APP_DIR}/
cd ${APP_DIR}

echo "[$(date)] Starting application server..."
sudo -u ${APP_USER} nohup java --add-opens java.base/sun.nio.ch=ALL-UNNAMED \
 "-Ddb.url=${DB_URL}" \
 -cp ${APP_DIR}/SkyenetApps-1.0.0.war com.simiacryptus.cognotik.AppServer --server > ${LOG_FILE} 2>&1 &
echo "[$(date)] Deployment complete."
```

### Current build script

```shell
#!/bin/bash
# Configuration
declare -A REPOS
REPOS=(
  ["/home/ec2-user/jo-penai"]="main"
  ["/home/ec2-user/Cognotik"]="main"
  ["/home/ec2-user/SkyenetApps"]="main"
)
S3_BUCKET="share.simiacrypt.us"
BUILD_LOG="build.log"
# Enable logging
exec 1> >(tee -a "deployment.log")
exec 2>&1
echo "[$(date)] Starting build process..."
# Function to update repository
update_repo() {
  local repo_path=$1
  local branch=$2
  echo "[$(date)] Updating repository: ${repo_path}"
  pushd ${repo_path}
  git reset --hard
  git clean -fdx
  git fetch origin
  git checkout ${branch}
  git pull origin ${branch}
  local status=$?
  popd
  return ${status}
}
# Update repositories
for repo in "${!REPOS[@]}"; do
  if ! update_repo "${repo}" "${REPOS[$repo]}"; then
    echo "[$(date)] ERROR: Failed to update ${repo}"
    exit 1
  fi
done

# Build application
echo "[$(date)] Building application..."
pushd "/home/ec2-user/SkyenetApps"  # SkyenetApps repository
chmod +x ./gradlew

if ! ./gradlew clean build -x test >${BUILD_LOG}; then
  echo "[$(date)] ERROR: Build failed. Check ${BUILD_LOG} for details."
  exit 1
fi

JARPATH=`realpath App/build/libs/App-1.0.0-optimized.jar`
echo "[$(date)] Built JAR: ${JARPATH}"

# Copy built artifacts to S3
echo "[$(date)] Copying artifacts to S3..."
if ! aws s3 cp build/libs/* s3://${S3_BUCKET}/libs/; then
  echo "[$(date)] ERROR: Failed to copy artifacts to S3"
  exit 1
fi
echo "[$(date)] Build process completed successfully."
popd
```