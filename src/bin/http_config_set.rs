use std::time::Duration;

use goose::prelude::*;
use ratelimiter_rs::QpsLimiter;

pub struct UserSession {
    pub limiter: QpsLimiter,
    pub uid: u64,
}

impl UserSession {
    fn new(uid: u64) -> Self {
        Self {
            limiter: QpsLimiter::new(300).set_burst_size(20),
            uid,
        }
    }
}

#[tokio::main]
async fn main() -> Result<(), GooseError> {
    GooseAttack::initialize()?
        .register_scenario(
            scenario!("ConfigSet")
                .set_weight(1)
                .unwrap()
                .register_transaction(transaction!(set_config).set_name("/nacos/v1/cs/configs")),
        )
        .execute()
        .await?;
    Ok(())
}

async fn set_config(user: &mut GooseUser) -> TransactionResult {
    naming_init_session(user)?;
    if let Some(user_session) = user.get_session_data_mut::<UserSession>() {
        if !user_session.limiter.acquire() {
            tokio::time::sleep(Duration::from_micros(1)).await;
            return Ok(());
        }
        let path = "/nacos/v1/cs/configs";
        let uuid = uuid::Uuid::new_v4();
        let data = format!(
            "dataId={:03}&group=DEFAULT_GROUP&tenant=public&content={}",
            &user_session.uid, uuid
        );
        let mut request_builder = user.get_request_builder(&GooseMethod::Post, path).unwrap();
        request_builder = request_builder
            .body(data)
            .header("Content-Type", "application/x-www-form-urlencoded");
        let goose_request = GooseRequest::builder()
            .set_request_builder(request_builder)
            .build();
        user.request(goose_request).await?;
    }
    Ok(())
}

fn naming_init_session(user: &mut GooseUser) -> TransactionResult {
    if let Some(_user_session) = user.get_session_data_mut::<UserSession>() {
    } else {
        let uid = user.weighted_users_index as u64;
        let user_session = UserSession::new(uid);
        user.set_session_data(user_session);
    };
    Ok(())
}
