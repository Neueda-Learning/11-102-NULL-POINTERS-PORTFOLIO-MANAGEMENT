package com.portfolio_management.portfolio.investments.stock;

import com.portfolio_management.portfolio.investments.asset.model.Asset;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "stock")
public class Stock {

	@Id
	@Column(name = "asset_id")
	private Long assetId;

	@MapsId
	@OneToOne(optional = false)
	@JoinColumn(name = "asset_id", nullable = false)
	private Asset asset;

	@Column(length = 60)
	private String exchange;

	@Column(length = 60)
	private String sector;

	public Long getAssetId() {
		return assetId;
	}

	public void setAssetId(Long assetId) {
		this.assetId = assetId;
	}

	public Asset getAsset() {
		return asset;
	}

	public void setAsset(Asset asset) {
		this.asset = asset;
	}

	public String getExchange() {
		return exchange;
	}

	public void setExchange(String exchange) {
		this.exchange = exchange;
	}

	public String getSector() {
		return sector;
	}

	public void setSector(String sector) {
		this.sector = sector;
	}
}
