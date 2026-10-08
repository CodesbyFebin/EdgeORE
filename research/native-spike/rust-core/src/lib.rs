//! Legacy DrillX computational spike. No protocol submission or earning claim.
use jni::{JNIEnv,objects::{JByteArray,JClass},sys::{jbyteArray,jlong}};
use std::time::{Instant,Duration};
const SIZE:usize=92;
/// LE: version:u32, flags:u32, attempts:u64, next_nonce:u64, best_nonce:u64,
/// elapsed_ns:u64, difficulty:u32, digest:[u8;16], hash:[u8;32].
/// Flags: bit0 has solution, bit1 nonce exhausted. An attempt is not a solution.
pub fn compute(challenge:&[u8], start:u64, budget_ms:u64)->Result<[u8;SIZE], &'static str>{
 let ch:&[u8;32]=challenge.try_into().map_err(|_|"CHALLENGE_LENGTH")?;
 if !(1..=250).contains(&budget_ms){return Err("BUDGET_RANGE");}
 let clock=Instant::now();let limit=Duration::from_millis(budget_ms);
 let mut next=start;let mut attempts=0u64;let mut flags=0u32;let mut best:Option<([u8;16],[u8;32],u64,u32)>=None;
 while clock.elapsed()<limit {
  let nonce=next;attempts+=1;
  if let Ok(h)=drillx::hash(ch,&nonce.to_le_bytes()) {
   let d=h.difficulty();
   if best.as_ref().map_or(true,|b|d>b.3){best=Some((h.d,h.h,nonce,d));}
  }
  match next.checked_add(1){Some(n)=>next=n,None=>{flags|=2;break;}}
 }
 let mut out=[0u8;SIZE];out[0..4].copy_from_slice(&1u32.to_le_bytes());
 out[8..16].copy_from_slice(&attempts.to_le_bytes());out[16..24].copy_from_slice(&next.to_le_bytes());
 out[32..40].copy_from_slice(&(clock.elapsed().as_nanos().min(u64::MAX as u128) as u64).to_le_bytes());
 if let Some((d,h,n,difficulty))=best {flags|=1;out[24..32].copy_from_slice(&n.to_le_bytes());out[40..44].copy_from_slice(&difficulty.to_le_bytes());out[44..60].copy_from_slice(&d);out[60..92].copy_from_slice(&h);}
 out[4..8].copy_from_slice(&flags.to_le_bytes());Ok(out)
}
#[no_mangle]
pub extern "system" fn Java_com_edgeore_mine_NativeMiner_mineChunk(mut env:JNIEnv<'_>,_class:JClass<'_>,input:JByteArray<'_>,start:jlong,budget:jlong)->jbyteArray {
 let result=std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
  let bytes=env.convert_byte_array(&input).map_err(|_|"JNI_READ_FAILED")?;
  if budget<1{return Err("BUDGET_RANGE");}
  let out=compute(&bytes,start as u64,budget as u64)?;
  env.byte_array_from_slice(&out).map(|a|a.into_raw()).map_err(|_|"JNI_ALLOCATION_FAILED")
 }));
 match result {Ok(Ok(bytes))=>bytes,Ok(Err(error))=>{let _=env.throw_new("java/lang/IllegalArgumentException",error);std::ptr::null_mut()},Err(_)=>{let _=env.throw_new("java/lang/IllegalStateException","NATIVE_COMPUTE_FAILED");std::ptr::null_mut()}}
}
#[cfg(test)]
mod tests {
 use super::*;
 #[test] fn refuses_invalid_challenge(){assert_eq!(compute(&[0;31],0,1).unwrap_err(),"CHALLENGE_LENGTH");}
 #[test] fn refuses_unbounded_budget(){assert_eq!(compute(&[0;32],0,251).unwrap_err(),"BUDGET_RANGE");}
 #[test] fn refuses_zero_budget(){assert_eq!(compute(&[0;32],0,0).unwrap_err(),"BUDGET_RANGE");}
 #[test] fn returned_solution_verifies_independently(){let c=[0u8;32];let out=compute(&c,4,250).unwrap();assert!(u64::from_le_bytes(out[8..16].try_into().unwrap())>0);assert_eq!(u32::from_le_bytes(out[4..8].try_into().unwrap())&1,1,"No successful solution in bounded fixture");{let n:&[u8;8]=out[24..32].try_into().unwrap();let d:&[u8;16]=out[44..60].try_into().unwrap();assert!(drillx::is_valid_digest(&c,n,d));let solution=drillx::Solution::new(*d,*n);assert_eq!(solution.to_hash().h.as_slice(),&out[60..92]);}}
}
