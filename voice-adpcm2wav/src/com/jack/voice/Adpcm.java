package com.jack.voice;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class Adpcm {

//	# table of  quantizer step size
	int[] stepSizeTable = {16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41,
	                 45, 50, 55, 60, 66, 73, 80, 88, 97, 107, 118, 130, 143, 157, 173,
	                 190, 209, 230, 253, 279, 307, 337, 371, 408, 449, 494, 544, 598, 658,
	                 724, 796, 876, 963, 1060, 1166, 1282, 1408, 1552};
//	# table of index
	int[] indexTable = {-1, -1, -1, -1, 2, 4, 6, 8, -1, -1, -1, -1, 2, 4, 6, 8};
//	# ADPCM_Encode.
//	# sample: a 16-bit PCM sample
//	# retval : a 4-bit ADPCM sample
	int predsample = 0;
	int index = 0;
	
	/**
	 * ADPCM 编码	
	 * 翻译c语言的实现，这部分没有验证过
	 * @param sample
	 * @return
	 */
	public byte ADPCM_Encode(int sample){
	    int code = 0;
	    int step_size = stepSizeTable[index];

//	    # compute diff and record sign and absolut value
	    int diff = sample - predsample;
	    if (diff < 0){
	    	code = 8;
	    	diff = -diff;
	    }
	        
//	    # quantize the diff into ADPCM code
//	    # inverse quantize the code into a predicted diff
	    int tmpstep = step_size;
	    int diffq = step_size >> 3;

	    if (diff >= tmpstep){
	    	code = code | 0x04;
	    	diff -= tmpstep;
	    	diffq = diffq + step_size;
	    }

	    tmpstep = tmpstep >> 1;

	    if (diff >= tmpstep){
	    	code = code | 0x02;
	    	diff = diff - tmpstep;
	    	diffq = diffq + (step_size >> 1);
	    }

	    tmpstep = tmpstep >> 1;

	    if (diff >= tmpstep){
	    	code = code | 0x01;
	    	diffq = diffq + (step_size >> 2);
	    }

//	    # fixed predictor to get new predicted sample
	    if ((code & 8)!=0){
	    	predsample = predsample - diffq;
	    } else {
	    	predsample = predsample + diffq;
	    }
	        
//	    # check for overflow
	    if (predsample > 32767){
	    	predsample = 32767;
	    }else if(predsample < -32768){
	    	predsample = -32768;
	    }	        

//	    # find new stepsize index
	    index += indexTable[code];

//	    # check for overflow
	    if (index < 0)
	        index = 0;

	    if (index > 48)
	        index = 48;

//	    # return new ADPCM code   code & 0x0f == code
	    return (byte)(code & 0x0f);//返回低4位字节的
	}
	
//	# ADPCM_Decode.
//	# code: a byte containing a 4-bit ADPCM sample.
//	# retval : 16-bit ADPCM sample
	int de_index = 0;
	int de_predsample = 0;
	
	/**
	 * ADPCM 解码   4Bit --> 16Bit
	 * @param code 1byte(包括一个4bit的ADPCM采样数据)
	 * @return short(一个16bit的PCM采样数据)
	 */
	public short ADPCM_Decode(int code){
	    int step_size = stepSizeTable[de_index];

//	    # inverse code into diff
	    int diffq = step_size >> 3;  //# == step/8
	    if ((code & 4) != 0){
	        diffq += step_size;
	    }

	    if ((code & 2) != 0){
	    	diffq += step_size >> 1;
	    }	        

	    if ((code & 1)!=0){
	    	diffq += step_size >> 2;
	    }	        

//	    # add diff to predicted sample
	    if ((code & 8)!=0){
	    	diffq = -diffq;
	    }	        

	    de_predsample += diffq;

//	    # check for overflow  clip the values to +/- 2^11 (supposed to be 16 bits)
	    if (de_predsample > 2047){
	        de_predsample = 2047;
	    }else if (de_predsample < -2048){
	        de_predsample = -2048;
	    }

//	    # find new quantizer step size
	    de_index += indexTable[code];

//	    # check for overflow
	    if (de_index < 0){
	        de_index = 0;
	    }
	    if (de_index > 48){
	        de_index = 48;
	    }
//	    # save predict sample and de_index for next iteration
//	    # return new decoded sample
//	    # The original algorithm turned out to be 12bit, need to convert to 16bit
	    return (short)(de_predsample << 4);
	}
	/**
	 * 
	 * ADPCM 转换为  WAV: vox(ADPCM,没有Header的音频数据) to wav(PCM)
	 * @param voxName 8000HZ 单通道 4bit	ADPCM
	 * @param wavName 8000HZ 单通道 16bit	PCM
	 * @param path
	 * @param endian True : big-endian（暂时不可以用）  false : little-endian
	 */
	public void convertAdpcmToWav(String voxName,String wavName,String path, boolean isBigEndian){
		File in = new File(path+voxName);
		File out = new File(path+wavName);
		try {
			FileInputStream fis = new FileInputStream(in);
			if(!out.exists()){
            	out.createNewFile();
            }
            FileOutputStream fos = new FileOutputStream(out);
            int length =fis.available();
            Header header = new Header(1, 8000, 16); 
            byte[] head = header.writeHeard(length,"PCM");
            assert head.length == 44;
            fos.write(head);
			int len = fis.available();
			byte[] list_8bit_bytes = new byte[len];
            int rLen = fis.read(list_8bit_bytes, 0, len);
            fis.close();
            for(int i=0;i<len;i++){
                byte byte_i = list_8bit_bytes[i];  //# 1 bytes = 8bit
                int high_4bit = (byte_i & 0xf0) >> 4; // # split high 4bit from 8bit
            	int low_4bit = byte_i & 0x0f; // # split low 4bit from 8bit

                short tmpDeS16_0 = ADPCM_Decode(high_4bit);
                short tmpDeS16_1 = ADPCM_Decode(low_4bit);
                if(isBigEndian){
                	fos.write(Header.HexByteBigEndian(tmpDeS16_0,2));
                	fos.write(Header.HexByteBigEndian(tmpDeS16_1,2));
                }else{
                	fos.write(Header.HexByteLittleEndian(tmpDeS16_0,2));
                    fos.write(Header.HexByteLittleEndian(tmpDeS16_1,2));
                }
            }
            fos.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	
	}

	/**
	 * 
	 * @param inByte	 单通道  4bit	ADPCM 音频数据
	 * @param outSamples 单通道 16bit	PCM 采样数据
	 * @param len
	 */
	public void convertAdpcmToPCMNoHeader(byte[] inByte, short[] outSamples){
		int j = 0;
		for(int i=0;i<inByte.length;i++){
            byte byte_i = inByte[i];  //# 1 bytes = 8bit
            int high_4bit = (byte_i & 0xf0) >> 4; // # split high 4bit from 8bit
        	int low_4bit = byte_i & 0x0f; // # split low 4bit from 8bit

//            # now decode
            short tmpDeS16_0 = ADPCM_Decode(high_4bit);
            short tmpDeS16_1 = ADPCM_Decode(low_4bit);         
            outSamples[j++] = tmpDeS16_0;
            outSamples[j++] = tmpDeS16_1;
        }
	}

	/**
	 * ADPCM 转换为  WAV: vox(ADPCM,没有Header的音频数据) to IMA-ADPCM(wave 格式中的一种压缩格式)
	 * @param voxName 8000HZ 单通道 4bit	ADPCM
	 * @param wavName 8000HZ 单通道  4bit	IMA-ADPCM 音频数据
	 * @param path
	 */
	public void convertAdpcmToImaADPCM(String voxName, String wavName,String path){
		File in = new File(path+voxName);
		File out = new File(path+wavName);
		try {
			if(!out.exists()){
            	out.createNewFile();
            }
            FileOutputStream fos = new FileOutputStream(out);
            FileInputStream fis = new FileInputStream(in);
            int length = fis.available() * 2; // 采样个数 = 数据长度(字节) *2(1字节包含2个4bit的采样)
            //组织 IMA-ADPCM的头
            Header header = new Header(1, 8000 , 4);
            byte[] head = header.writeHeard(length,"ADPCM");
            fos.write(head);
            //组织 IMA-ADPCM的头
            
            //初始化 IMA-ADPCM的 blockHead头
			BlockHeader blockheard = new BlockHeader();
			// 设置默认的index和rsv
			blockheard.setBLOCKIndex(0);
			blockheard.setBLOCKRSV(0);
			//初始化 IMA-ADPCM的 blockHead头
			
			// 初始临时变量
			int blockHeadNum = (int) (length/505);
			int lastBlockSampleNum = length - blockHeadNum*505 -1;// 最后一个block的data包含的采样个数
			if(length % 505 != 0){ // 存在不足一个block的情况
				blockHeadNum += 1;
			}
			int currentNum = 1;
			byte[] headBytes = new byte[1];
			byte[] dataBytes = new byte[1];
			int temp = 1;
			boolean flag = true;//上一个 原始数据是否高4位、低4位 均已处理完成
			int blockDataSie = 252;// block的data部分字节数
			while(currentNum <= blockHeadNum){
				int headSample = 0; // 存储在blockHead 中的采样数据
				if(flag){
					fis.read(headBytes);
					headSample = (headBytes[0] & 0xf0) >> 4; // # split high 4bit from 8bit
					temp = headBytes[0] & 0x0f; // # split low 4bit from 8bit
					flag = !flag;
				}else{
					headSample = temp;
					temp = 0;
					flag = !flag;
				}
//	            # 解码第一个采样数据
	            int tmpDeS16_0 = ADPCM_Decode(headSample);
	            blockheard.setBLOCKPresample(tmpDeS16_0);
                // blockheard 的 4 bytes()
                fos.write(Header.HexByteBigEndian(blockheard.getBLOCKPresample(), 2));
                fos.write(Header.HexByteBigEndian(blockheard.getBLOCKIndex(), 1));
                fos.write(Header.HexByteBigEndian(blockheard.getBLOCKRSV(), 1));
                // blockheard 的 4 bytes()
                
                // blockData 的 252字节数据处理
                if(currentNum == blockHeadNum){// 最后一个不足252*2个采样的block处理
                	blockDataSie = lastBlockSampleNum /= 2;
	                if(lastBlockSampleNum % 2 != 0){
	                	blockDataSie += 1;
	                }
	            }
                
                dataBytes = new byte[blockDataSie];
        		fis.read(dataBytes);
                if(!flag){
            		fos.write(dataBytes);
            	}else{ // 上一个字节数据 还有低4bit 数据待处理: 需要转换为高4位数据 与 下一个数据的结合为一个字节 
            		for (int i = 0; i < dataBytes.length; i++) {
            			temp = (byte)(temp << 4) | (byte)((dataBytes[i] & 0xf0) >> 4);
            			fos.write(temp);
            			temp = dataBytes[i] & 0x0f;// split 最后一个数据的 low 4bit from 8bit，下次循环使用
					} 
            	}
	            // blockData 的 252字节数据处理
	            
	            // index++
				currentNum++;
			}

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	
	}


	/**
	 * decode: 
	 * @param args
	 */
	public static void main(String[] args) {
		
		Adpcm a = new Adpcm();
//		a.convertAdpcmToWav("win7_16bit_8k_mono.adpcm", "test20210627.wav", "E:\\workSpace\\ADPCMVoice\\voice\\",false);
		a.convertAdpcmToImaADPCM("win7_16bit_8k_mono.adpcm", "test20210627_ADPCM.wav", "E:\\workSpace\\ADPCMVoice\\voice\\");
		
	}
	
}
